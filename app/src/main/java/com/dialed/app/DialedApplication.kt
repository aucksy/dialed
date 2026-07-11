package com.dialed.app

import android.app.Application

/**
 * Application scope. Phase 2 adds the BillingManager here; Phase 4 the WatchConnection
 * (CapabilityClient/MessageClient/ChannelClient sender) so they outlive any Activity.
 */
class DialedApplication : Application()
