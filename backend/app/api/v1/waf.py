from __future__ import annotations

import re
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import PlainTextResponse
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import current_user
from app.db.models import Site, TrafficPoint, User, WafEvent, WafRule
from app.db.seed import load_rules
from app.db.session import get_db
from app.schemas import InspectIn, WafRuleIn, WafRulePatch
from app.services import buckets, waf

router = APIRouter(prefix="/waf", tags=["waf"])

VALID_TARGET = {"path", "ua", "query", "body"}
VALID_ACTION = {"block", "challenge", "log"}
VALID_SEVERITY = {"low", "medium", "high"}
ACTION_LABEL = {"block": "拦截", "challenge": "质询", "log": "记录"}


def _rule_dto(rule: WafRule) -> dict:
    return {
        "id": rule.id,
        "name": rule.name,
        "pattern": rule.pattern,
        "target": rule.target,
        "action": rule.action,
        "severity": rule.severity,
        "enabled": rule.enabled,
        "builtin": rule.builtin,
        "created_at": rule.created_at.isoformat(sep=" ") if rule.created_at else None,
    }


def _event_dto(event: WafEvent) -> dict:
    return {
        "id": event.id,
        "site_id": event.site_id,
        "ip": event.ip,
        "method": event.method,
        "path": event.path,
        "ua": event.ua,
        "action": event.action,
        "rule_id": event.rule_id,
        "rule_name": event.rule_name,
        "score": event.score,
        "created_at": event.created_at.isoformat(sep=" ") if event.created_at else None,
    }


async def _user_site_ids(db: AsyncSession, user: User) -> list[int]:
    return list((await db.execute(select(Site.id).where(Site.user_id == user.id))).scalars().all())


def _scope(column, site_ids: list[int]):
    """用户可见范围：自己的站点 + 全局事件（site_id 为空）。"""
    if site_ids:
        return or_(column.is_(None), column.in_(site_ids))
    return column.is_(None)


@router.get("/rules")
async def list_rules(
    user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> list[dict]:
    rows = (await db.execute(select(WafRule).order_by(WafRule.builtin.desc(), WafRule.id))).scalars().all()
    return [_rule_dto(r) for r in rows]


@router.post("/rules", status_code=status.HTTP_201_CREATED)
async def create_rule(
    payload: WafRuleIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    try:
        re.compile(payload.pattern)
    except re.error as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"正则表达式无效：{exc}") from exc
    rule = WafRule(
        name=payload.name,
        pattern=payload.pattern,
        target=payload.target,
        action=payload.action,
        severity=payload.severity,
        enabled=True,
        builtin=False,
    )
    db.add(rule)
    await db.commit()
    await db.refresh(rule)
    return _rule_dto(rule)


@router.patch("/rules/{rule_id}")
async def patch_rule(
    rule_id: int,
    payload: WafRulePatch,
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    rule = await db.get(WafRule, rule_id)
    if rule is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "规则不存在")
    if payload.pattern is not None:
        try:
            re.compile(payload.pattern)
        except re.error as exc:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, f"正则表达式无效：{exc}") from exc
        rule.pattern = payload.pattern
    if payload.name is not None:
        rule.name = payload.name
    if payload.target is not None:
        if payload.target not in VALID_TARGET:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "target 不合法")
        rule.target = payload.target
    if payload.action is not None:
        if payload.action not in VALID_ACTION:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "action 不合法")
        rule.action = payload.action
    if payload.severity is not None:
        if payload.severity not in VALID_SEVERITY:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "severity 不合法")
        rule.severity = payload.severity
    if payload.enabled is not None:
        rule.enabled = payload.enabled
    await db.commit()
    return _rule_dto(rule)


@router.delete("/rules/{rule_id}")
async def delete_rule(
    rule_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    rule = await db.get(WafRule, rule_id)
    if rule is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "规则不存在")
    if rule.builtin:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "内置规则只能停用，不能删除")
    await db.delete(rule)
    await db.commit()
    return {"message": "规则已删除"}


@router.get("/events")
async def list_events(
    limit: int = Query(default=30, ge=1, le=200),
    site_id: int | None = Query(default=None),
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
) -> list[dict]:
    site_ids = await _user_site_ids(db, user)
    stmt = select(WafEvent).order_by(WafEvent.id.desc()).limit(limit)
    if site_id is not None:
        if site_id not in site_ids:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "站点不存在")
        stmt = stmt.where(WafEvent.site_id == site_id)
    else:
        stmt = stmt.where(_scope(WafEvent.site_id, site_ids))
    rows = (await db.execute(stmt)).scalars().all()
    return [_event_dto(r) for r in rows]


@router.get("/stats")
async def stats(
    range: str = Query(default="24h", alias="range"),
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    span, _bucket = buckets.resolve(range)
    since = buckets.utcnow() - timedelta(seconds=span)
    site_ids = await _user_site_ids(db, user)

    scanned = 0
    if site_ids:
        scanned = int(
            (
                await db.execute(
                    select(func.coalesce(func.sum(TrafficPoint.requests), 0)).where(
                        TrafficPoint.site_id.in_(site_ids), TrafficPoint.ts >= since
                    )
                )
            ).scalar_one()
        )

    event_scope = _scope(WafEvent.site_id, site_ids)
    blocked = int(
        (
            await db.execute(
                select(func.count())
                .select_from(WafEvent)
                .where(WafEvent.created_at >= since, WafEvent.action == "block", event_scope)
            )
        ).scalar_one()
    )

    attack_rows = (
        await db.execute(
            select(WafEvent.rule_name, func.count().label("c"))
            .where(WafEvent.created_at >= since, event_scope)
            .group_by(WafEvent.rule_name)
            .order_by(func.count().desc())
            .limit(5)
        )
    ).all()
    ip_rows = (
        await db.execute(
            select(WafEvent.ip, func.count().label("c"))
            .where(WafEvent.created_at >= since, event_scope)
            .group_by(WafEvent.ip)
            .order_by(func.count().desc())
            .limit(5)
        )
    ).all()

    return {
        "range": range,
        "scanned": scanned,
        "blocked": blocked,
        "top_attacks": [{"label": name or "未分类", "value": int(count)} for name, count in attack_rows],
        "top_ips": [{"label": ip or "未知", "value": int(count)} for ip, count in ip_rows],
    }


@router.post("/inspect")
async def inspect_request(
    payload: InspectIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    rules = await load_rules(db)
    decision = waf.inspect(
        rules,
        path=payload.path,
        ua=payload.ua,
        query=payload.query,
        body=payload.body,
        method=payload.method,
    )
    return {
        "verdict": decision.verdict,
        "verdict_label": ACTION_LABEL.get(decision.verdict, "放行"),
        "score": decision.score,
        "matches": decision.matches,
        "rules_loaded": len(rules),
    }


@router.get("/edge/nginx", response_class=PlainTextResponse)
async def nginx_snippet(
    user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> str:
    """把启用中的规则导出成 Nginx 片段，贴到站点 server{} 里即可真正拦截。"""
    rules = (await db.execute(select(WafRule).where(WafRule.enabled.is_(True)))).scalars().all()
    lines = [
        "# LanOS S.A.F.E 自动生成 —— 粘贴到对应站点的 server { } 内",
        "# 说明：正则能力有限，复杂规则（如环视）请在 OpenResty 侧用 Lua 实现",
        "",
    ]
    for rule in rules:
        pattern = rule.pattern.replace('"', '\\"')
        var = {"path": "$request_uri", "ua": "$http_user_agent", "query": "$args", "body": "$request_body"}.get(
            rule.target, "$request_uri"
        )
        if rule.action == "block":
            lines.append(f'# {rule.name}（{rule.severity}）')
            lines.append(f'if ({var} ~* "{pattern}") {{ return 403; }}')
        elif rule.action == "challenge":
            lines.append(f'# {rule.name}（{rule.severity}，质询）')
            lines.append(f'if ({var} ~* "{pattern}") {{ return 302 https://challenge.lanos.local/; }}')
        else:
            lines.append(f'# {rule.name}（{rule.severity}，仅记录，需配合日志）')
        lines.append("")
    return "\n".join(lines)
