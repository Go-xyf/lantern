package org.lantern.http;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang3.StringUtils;
import org.lantern.Events;
import org.lantern.LanternUtils;
import org.lantern.event.SyncEvent;
import org.lantern.state.InternalState;
import org.lantern.state.Modal;
import org.lantern.state.Model;
import org.lantern.state.ModelChangeImplementor;
import org.lantern.state.Settings.Mode;
import org.lantern.state.SyncPath;
import org.lantern.state.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class InteractionServlet extends HttpServlet {

    private final InternalState internalState;
    
    private enum Interaction {
        GET,
        GIVE,
        CONTINUE,
        SETTINGS,
        CLOSE,
    }
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    /**
     * Generated serialization ID.
     */
    private static final long serialVersionUID = -8820179746803371322L;

    private final ModelChangeImplementor changeImplementor;

    private final SyncService syncService;

    private final Model model;
    
    @Inject
    public InteractionServlet(final Model model, 
        final ModelChangeImplementor changeImplementor,
        final SyncService syncService, final InternalState internalState) {
        this.model = model;
        this.changeImplementor = changeImplementor;
        this.syncService = syncService;
        this.internalState = internalState;
    }
    
    @Override
    protected void doGet(final HttpServletRequest req, 
        final HttpServletResponse resp) throws ServletException, 
        IOException {
        processRequest(req, resp);
    }
    @Override
    protected void doPost(final HttpServletRequest req, 
        final HttpServletResponse resp) throws ServletException, 
        IOException {
        processRequest(req, resp);
    }
    
    protected void processRequest(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        final String uri = req.getRequestURI();
        log.info("Received URI: {}", uri);
        final Map<String, String> params = HttpUtils.toParamMap(req);
        log.info("Params: {}", params);
        final String interactionStr = params.get("interaction");
        if (StringUtils.isBlank(interactionStr)) {
            log.info("No interaction!!");
            HttpUtils.sendClientError(resp, "interaction argument required!");
            return;
        }
        
        final Interaction inter = Interaction.valueOf(interactionStr.toUpperCase());
        
        final Modal modal = this.model.getModal();
        switch (modal) {
        case welcome:
            switch (inter) {
            case GET:
                log.info("Setting get mode");
                handleGiveGet(true);
                break;
            case GIVE:
                log.info("Setting give mode");
                handleGiveGet(false);
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case about:
            break;
        case authorize:
            break;
        case finished:
            switch (inter) {
            case CONTINUE:
                log.info("Processing continue");
                this.model.setShowVis(true);
                this.model.setSetupComplete(true);
                this.internalState.setModalCompleted(Modal.finished);
                this.internalState.advanceModal(null);
                Events.asyncEventBus().post(new SyncEvent(SyncPath.ALL, model));
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case firstInviteReceived:
            break;
        case gtalkUnreachable:
            break;
        case inviteFriends:
            switch (inter) {
            case CONTINUE:
                log.info("Processing continue");
                this.internalState.setModalCompleted(Modal.inviteFriends);
                this.internalState.advanceModal(null);
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case none:
            switch (inter) {
            case SETTINGS:
                log.info("Processing settings");
                this.model.setModal(Modal.settings);
                syncModal();
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case notInvited:
            break;
        case proxiedSites:
            switch (inter) {
            case CONTINUE:
                log.info("Processing continue");
                // How should we actually set the proxied sites here?
                this.internalState.setModalCompleted(Modal.proxiedSites);
                this.internalState.advanceModal(null);
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case requestInvite:
            break;
        case requestSent:
            break;
        case settings:
            switch (inter) {
            case CLOSE:
                log.info("Processing settings close");
                this.model.setModal(Modal.none);
                syncModal();
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case settingsLoadFailure:
            break;
        case systemProxy:
            switch (inter) {
            case CONTINUE:
                log.info("Processing continue");
                final boolean sys = toBool(params.get("systemProxy"));
                this.model.getSettings().setSystemProxy(sys);
                this.internalState.setModalCompleted(Modal.systemProxy);
                this.internalState.advanceModal(null);
                break;
            default:
                HttpUtils.sendClientError(resp, "give or get required");
                break;
            }
            break;
        case updateAvailable:
            break;
        case authorizeLater:
            break;
        case confirmReset:
            break;
        case contactDevs:
            break;
        case giveModeForbidden:
            break;
        case passwordCreate:
            break;
        default:
            log.info("No matching modal for {}", modal);
        }
    }

    private void syncModal() {
        Events.asyncEventBus().post(new SyncEvent(SyncPath.MODAL, model.getModal()));
    }

    private boolean toBool(final String str) {
        final String norm = str.toLowerCase().trim();
        return (norm.equals("true") || norm.equals("on"));
    }

    private void handleGiveGet(final boolean getMode) {
        this.model.getSettings().setMode(getMode ? Mode.get : Mode.give);
        this.model.setModal(SystemUtils.IS_OS_LINUX ? Modal.passwordCreate : Modal.authorize);
        //this.syncService.publishSync("", this.model.getSettings().getMode());
        this.syncService.publishSync("settings.mode", this.model.getSettings().getMode());
        //this.syncService.publishSync("modal", this.model.getModal());
        
        syncModal();
        this.internalState.setModalCompleted(Modal.welcome);
        this.changeImplementor.setGetMode(getMode);
    }

}
