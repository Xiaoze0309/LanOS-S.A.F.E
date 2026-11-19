"""桌面端 / 绿色版入口：给 PyInstaller 打包用，也可以直接 `python launcher.py` 跑。

打包后是一个单文件可执行程序（Windows 为 .exe），运行目录下放 .env 即可配置。
"""

from __future__ import annotations

import os
import sys
from pathlib import Path


def _base_dir() -> Path:
    if getattr(sys, "frozen", False):  # PyInstaller 单文件模式
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def _ensure_env(base: Path) -> None:
    """首次运行且没有 .env 时，从内置模板生成一份（DEV_MODE=true，方便自测）。"""
    env = base / ".env"
    if env.exists():
        return
    example = base / ".env.example"
    if not example.exists() and getattr(sys, "frozen", False):
        bundle = getattr(sys, "_MEIPASS", None)
        if bundle:
            example = Path(bundle) / ".env.example"
    try:
        if example.exists():
            env.write_text(example.read_text(encoding="utf-8"), encoding="utf-8")
            print("已生成 .env（DEV_MODE=true），可自行修改后重启")
    except OSError as exc:
        print(f"生成 .env 失败（可手动复制 .env.example）：{exc}")


def main() -> None:
    base = _base_dir()
    os.chdir(base)  # 保证 .env 与程序同目录时能被 pydantic-settings 读到
    _ensure_env(base)
    import uvicorn

    from app.main import app

    host = os.environ.get("LANOS_HOST", "0.0.0.0")
    port = int(os.environ.get("LANOS_PORT", "8000"))
    print(f"LanOS S.A.F.E backend -> http://{host}:{port}  (docs: /docs)")
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
