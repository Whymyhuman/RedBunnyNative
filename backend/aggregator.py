import requests
import re
import asyncio
import aiohttp
import time
import base64
import json
import subprocess
import os
import urllib.parse

# --- SUMBER RAW IP (HTTP/SOCKS) ---
# Kita butuh IP:Port sebanyak-banyaknya
SOURCES = [
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt",
    "https://raw.githubusercontent.com/saisuiu/Lionkings-Http-Proxys-Proxies/main/free.txt",
    "https://raw.githubusercontent.com/caliphdev/Proxy-List/master/http.txt",
    "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
    "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
    "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
    "https://raw.githubusercontent.com/shiftytr/proxy-list/master/proxy.txt",
    "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt"
]

# Regex IP:Port Simple
REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')

# Target Check: Speedtest API (Ringan & Cepat)
TARGET_URL = "https://www.speedtest.net/api/js/servers?engine=js"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

# --- TEMPLATE VLESS DAUSLEARN ---
# UUID: 96900e2c-fd86-4207-abc1-91ac57cf931d
# Host: dauslearn.dpdns.org
# Path Format: /IP-PORT
def create_tunnel_link(ip, port):
    # Path harus: /IP-PORT (titik dua diganti dash)
    # Contoh: 1.1.1.1:80 -> /1.1.1.1-80
    # Di URL VLESS, '/' di awal path harus di-encode jadi %2F
    
    clean_path = f"/{ip}-{port}"
    # Kita construct manual stringnya agar persis permintaan user
    # path=%2F149.129.226.9-194
    
    link = (
        f"vless://96900e2c-fd86-4207-abc1-91ac57cf931d@dauslearn.dpdns.org:443/"
        f"?type=ws&encryption=none&flow=&"
        f