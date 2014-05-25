(ns qarth.friend
  "Friend workflows for Qarth."
  (require (qarth [oauth :as oauth]
                  [ring :as qarth-ring])
           [cemerick.friend :as friend]
           (ring.util request response)
           [clojure.tools.logging :as log]))

; TODO figure out friend credential-fns and friend credential-maps.
(defn credential-map
  "Adds Friend meta-information to the auth session, and uses that
  as the credential map."
  [credential-fn sesh redirect-on-auth?]
  (vary-meta (credential-fn sesh) assoc
             ::friend/workflow ::qarth
             ::friend/redirect-on-auth? redirect-on-auth?
             :type ::friend/auth))

; TODO multi-workflow
; TODO make requests from friend
; TODO user principal cred fn
(defn workflow
  "Creates a Friend workflow using a Qarth service.

  Required arguments:
  service -- the auth service
  auth-url -- A dual purpose URL. This starts both the OAuth workflow
  (so a login button, for example, should redirect here)
  and serves as the auth callback.
  It should be the same as the callback in your auth service.

  Optional arguments:
  login-url -- a URL to redirect to if a user is not logged in.
  credential-fn -- override Friend credential fn (default is identity)
  redirect-on-auth? -- the Friend redirect on auth setting, default true
  login-failure-handler -- the login failure handler.
  Default is to use the Friend login-failure-handler, redirect to a
  configured login-url or redirect to the Friend :login-uri while
  preserving the current session.
  (The default Friend :login-uri is /login. Note that Friend
  pedantically uses URI and not URL.)

  The workflow's returned credentials are the Qarth session,
  with Friend metadata attached.
  Exceptions are logged and countered as auth failures."
  [{:keys [service auth-url credential-fn redirect-on-auth?
           login-url login-failure-handler]}]
  (let [redirect-handler (qarth-ring/auth-redirect-handler service)]
    (fn [{ring-sesh :session :as req}]
      (let [auth-config (::friend/auth-config req)
            auth-url (or auth-url (:auth-url auth-config))]
        (if (= (ring.util.request/path-info req) auth-url)
          (let [auth-config (::friend/auth-config req)
                redirect-on-auth? (or redirect-on-auth?
                                      (:redirect-on-auth? auth-config) true)
                credential-fn (or credential-fn
                                  (get auth-config :credential-fn)
                                  identity)
                success-handler (fn [{{sesh ::oauth/session} :session}]
                                  (credential-map credential-fn sesh
                                                  redirect-on-auth?))
                login-failure-handler (or login-failure-handler
                                          (get auth-config :login-failure-handler)
                                          (fn [req]
                                            (assoc
                                              (ring.util.response/redirect
                                                (or login-url
                                                    (:login-url auth-config)
                                                    ; Should be url, not uri, because
                                                    ; all Friend uris are URLs.
                                                    (:login-uri auth-config)))
                                              :session (:session req))))]
            ((qarth-ring/auth-handler service
                                      success-handler
                                      login-failure-handler) req)))))))
