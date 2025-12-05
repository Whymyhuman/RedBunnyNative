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
import random

SOURCES = [
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt"
]

REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')
TARGET_URL = "https://www.speedtest.net/api/js/servers?engine=js"
XRAY_BIN = "backend/xray_bin/xray"
LOCAL_PORT_START = 10000

def create_tunnel_link(ip, port):
    # Encode path dengan benar: /IP-PORT -> %2FIP-PORT
    raw_path = f"/{ip}-{port}"
    encoded_path = urllib.parse.quote(raw_path, safe='')
    
    base = "vless://96900e2c-fd86-4207-abc1-91ac57cf931d@dauslearn.dpdns.org:443"
    params = (
        "?type=ws&"
        "encryption=none&"
        "flow=&"
        "host=media-sin6-3.cdn.whatsapp.net.dauslearn.dpdns.org&"
        f"path={encoded_path}&"
        "security=tls&"
        "sni=media-sin6-3.cdn.whatsapp.net.dauslearn.dpdns.org"
    )
    hashtag = f"#Tunnel-{ip}"
    return f"{base}/{params}{hashtag}"

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=10) as response:
            if response.status == 200: return await response.text()
    except: pass
    return ""

def parse_vless(uri):
    try:
        if not uri.startswith("vless://"): return None
        main = uri.replace("vless://", "")
        if "@" not in main: return None
        uuid, rest = main.split("@", 1)
        
        if "#" in rest: addr, _ = rest.split("#", 1)
        else: addr = rest
            
        if "?" in addr: addr_port, params_str = addr.split("?", 1)
        else: return None
            
ip, port = addr_port.split(":")
        params = dict(urllib.parse.parse_qsl(params_str))
        
        return {
            "uuid": uuid, "ip": ip, "port": int(port),
            "type": params.get("type", "tcp"),
            "path": params.get("path", "/"),
            "host": params.get("host", ""),
            "sni": params.get("sni", ""),
            "security": params.get("security", "none")
        }
    except: return None

def generate_xray_config(vless, port):
    outbound = {
        "protocol": "vless",
        "settings": {
            "vnext": [{"address": vless["ip"], "port": vless["port"], "users": [{"id": vless["uuid"], "encryption": "none"}]}]
        },
        "streamSettings": {
            "network": vless["type"],
            "security": vless["security"],
            "wsSettings": {"path": vless["path"], "headers": {"Host": vless["host"]}} if vless["type"] == "ws" else None,
            "tlsSettings": {"serverName": vless["sni"], "allowInsecure": True} if vless["security"] == "tls" else None
        }
    }
    return json.dumps({
        "log": {"loglevel": "none"},
        "inbounds": [{"port": port, "listen": "127.0.0.1", "protocol": "socks", "settings": {"udp": True}}],
        "outbounds": [outbound]
    })

async def check_proxy(uri, idx):
    vless = parse_vless(uri)
    if not vless: return None
    
    port = LOCAL_PORT_START + (idx % 50)
    cfg_file = f"config_{{port}}.json"
    
    with open(cfg_file, "w") as f: f.write(generate_xray_config(vless, port))
        
    proc = None
    try:
        proc = subprocess.Popen([XRAY_BIN, "-c", cfg_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        await asyncio.sleep(1)
        
        async with aiohttp.ClientSession() as sess:
            # Gunakan http://google.com/generate_204 (sangat ringan) untuk cek koneksi tunnel
            # Speedtest API kadang memblokir datacenter IP (GitHub Actions)
            async with sess.get("http://www.gstatic.com/generate_204", proxy=f"socks5://127.0.0.1:{port}", timeout=5) as resp:
                if resp.status == 204:
                    return uri
    except: pass
    finally:
        if proc: proc.kill()
        if os.path.exists(cfg_file): os.remove(cfg_file)
    return None

async def main():
    print("🚀 Starting Tunnel Checker...")
    raw_ips = set()
    async with aiohttp.ClientSession() as sess:
        tasks = [fetch_source(sess, u) for u in SOURCES]
        results = await asyncio.gather(*tasks)
        for c in results:
            if c: raw_ips.update([(m[0], m[1]) for m in REGEX_IP_PORT.findall(c)])

    print(f"📥 Found {len(raw_ips)} raw proxies.")
    
    candidates = [create_tunnel_link(ip, p) for ip, p in raw_ips]
    random.shuffle(candidates)
    
    # Limit 500 checks to save time & server load
    candidates = candidates[:500]
    
    print(f"🔍 Checking {len(candidates)} samples...")
    
    sem = asyncio.Semaphore(20)
    async def sem_check(p, i):
        async with sem: return await check_proxy(p, i)

    tasks = [sem_check(p, i) for i, p in enumerate(candidates)]
    results = await asyncio.gather(*tasks)
    active = [r for r in results if r]
    
    print(f"✅ Verified Working: {len(active)}")
    
    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(active))

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())