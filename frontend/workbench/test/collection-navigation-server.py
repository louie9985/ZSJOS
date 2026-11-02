# -*- coding: utf-8 -*-
"""Read-only synthetic page host with SPA fallback; no proxy to business services."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[1]/'tmp/collection-navigation'
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs): super().__init__(*args,directory=str(ROOT),**kwargs)
    def do_GET(self):
        if urlsplit(self.path).path.startswith('/zsjos/'):
            self.path='/index.html'
        super().do_GET()
if __name__=='__main__': ThreadingHTTPServer(('127.0.0.1',5198),Handler).serve_forever()
