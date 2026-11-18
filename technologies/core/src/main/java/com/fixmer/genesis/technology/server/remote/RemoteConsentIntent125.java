package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.remote.RemoteConsent72.Frame;

/** A local click survives packet reordering, but never a different offer or consent ticket. */
public final class RemoteConsentIntent125 {
    private Frame accepted;
    public void accept(Frame verified){if(!verified.phase().equals("ACTIVE")||!verified.decision().equals("PENDING")||verified.remaining()==0)throw new IllegalArgumentException("CONSENT");accepted=verified;}
    public void clear(){accepted=null;}
    public boolean permits(Frame current){
        var a=accepted;return a!=null&&current!=null&&current.phase().equals("ACTIVE")&&current.decision().equals("ACCEPTED")
            &&a.instance().equals(current.instance())&&a.round().equals(current.round())&&a.ticket().equals(current.ticket())
            &&a.release().equals(current.release())&&a.distribution().equals(current.distribution())&&a.sha().equals(current.sha())
            &&a.game().equals(current.game())&&a.endpoint().equals(current.endpoint())&&a.signer().equals(current.signer())&&a.revision()==current.revision();
    }
}
