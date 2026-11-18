"""Install the existing publisher identity into an idle dedicated server. No key generation/rotation."""
import argparse,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from first58.runtime import core_classpath

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project');p.add_argument('server');p.add_argument('publisher_identity');p.add_argument('--java',default='java');a=p.parse_args()
 root=Path(a.project).resolve()
 return subprocess.run([a.java,'-cp',str(core_classpath(root)),'com.fixmer.genesis.technology.server.remote.ControlSetup73',str(Path(a.server).resolve()),str(Path(a.publisher_identity).resolve())]).returncode
if __name__=='__main__':raise SystemExit(main())
