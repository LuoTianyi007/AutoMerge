"""Fetch the three pinned runtime binaries into an existing Android tools directory."""
import argparse, hashlib, json, pathlib, urllib.request
p=argparse.ArgumentParser();p.add_argument('--tools',required=True);args=p.parse_args()
root=pathlib.Path(__file__).resolve().parent;tools=pathlib.Path(args.tools).resolve();tools.mkdir(parents=True,exist_ok=True)
for name,item in json.loads((root/'dependency-manifest.json').read_text())['binaries'].items():
    target=tools/name
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=item['sha256']:
        with urllib.request.urlopen(item['url']) as response: data=response.read()
        if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Download hash mismatch: '+name)
        target.write_bytes(data)
    print('Verified',name)
