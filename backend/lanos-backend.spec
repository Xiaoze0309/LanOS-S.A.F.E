# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller 打包配置：在 backend/ 目录下执行

    pip install pyinstaller
    pyinstaller lanos-backend.spec

产物：dist/lanos-backend（Windows 上为 lanos-backend.exe），单文件、自带 Python 运行时。
"""

from PyInstaller.utils.hooks import collect_submodules

hidden = collect_submodules("uvicorn") + [
    "asyncmy",
    "asyncmy.connection",
    "asyncmy.cursors",
    "asyncmy.consts",
    "sqlalchemy.dialects.mysql.asyncmy",
    "sqlalchemy.dialects.mysql.aiomysql",
    "pydantic_settings",
]

a = Analysis(
    ["launcher.py"],
    pathex=["."],
    binaries=[],
    datas=[(".env.example", ".")],
    hiddenimports=hidden,
    hookspath=[],
    runtime_hooks=[],
    excludes=["tkinter", "pytest"],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="lanos-backend",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
)
