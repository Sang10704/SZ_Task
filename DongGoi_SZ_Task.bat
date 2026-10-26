@echo off
chcp 65001 >nul
title SeaZen Task - Công Cụ Đóng Gói APK

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

python package_tool.py

if %ERRORLEVEL% neq 0 (
    echo.
    echo Co loi xay ra trong qua trinh dong goi.
    pause
)
