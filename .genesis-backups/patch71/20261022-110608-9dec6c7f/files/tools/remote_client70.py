#!/usr/bin/env python3
"""Private one-use worker entry point, started by Minecraft's native UI only."""
from pathlib import Path
import sys
from first58.common import safe,json_write
from first58.remote70 import Worker
if __name__=='__main__':
 folder=safe(Path(sys.argv[1]))
 try:
  from first_launch import ownership
  with ownership(folder):
   if (folder/'started70').exists():raise SystemExit(2)
   Worker(folder).run()
 except Exception:
  # No raw exception/argv/token in the launcher log. Always leave a discoverable report.
  json_write(folder/'bootstrap-failure70.json',{'schema':70,'phase':'FAILED','reason':'Worker bootstrap rejected; original profile was not modified.'})
  raise SystemExit(2)
