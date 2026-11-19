"""预置 WAF 规则：首次启动时写入一次，之后可在 App 里启停/删除（自定义规则可增删）。"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import WafRule
from app.services import waf


async def ensure_builtin_rules(db: AsyncSession) -> int:
    total = (await db.execute(select(func.count()).select_from(WafRule))).scalar_one()
    if total:
        return 0
    for rule in waf.DEFAULT_RULES:
        db.add(
            WafRule(
                name=rule.name,
                pattern=rule.pattern,
                target=rule.target,
                action=rule.action,
                severity=rule.severity,
                enabled=True,
                builtin=True,
            )
        )
    await db.commit()
    return len(waf.DEFAULT_RULES)


async def load_rules(db: AsyncSession) -> list[waf.Rule]:
    rows = (
        await db.execute(select(WafRule).where(WafRule.enabled.is_(True)).order_by(WafRule.id))
    ).scalars().all()
    return [
        waf.Rule(
            name=r.name,
            pattern=r.pattern,
            target=r.target,
            action=r.action,
            severity=r.severity,
            rule_id=r.id,
        )
        for r in rows
    ]
