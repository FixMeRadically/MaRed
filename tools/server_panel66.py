#!/usr/bin/env python3
"""Headless worker; its only configuration UI is inside Minecraft."""
from pathlib import Path
import sys
from first58.panel66 import Worker
if __name__=='__main__':
 try:
  if len(sys.argv)!=2:raise ValueError('Panel runtime argument')
  Worker(Path(sys.argv[1])).run()
 except Exception as error:raise SystemExit('Genesis server panel: '+type(error).__name__)
