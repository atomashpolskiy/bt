package bt.it.fixture;

import bt.runtime.BtRuntimeBuilder;

interface SwarmPeerFactory {

    SwarmPeer createSeeder(BtRuntimeBuilder runtimeBuilder);

    SwarmPeer createLeecher(BtRuntimeBuilder runtimeBuilder);
}
