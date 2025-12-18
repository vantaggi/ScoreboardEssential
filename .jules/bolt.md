## 2024-05-24 - [Wear OS Connectivity Optimization]
**Learning:** Polling `getCapability` every 5 seconds is inefficient and drains battery. The `CapabilityClient.OnCapabilityChangedListener` allows for event-driven updates, removing the need for polling.
**Action:** Always prefer `addListener` over polling for Wear OS capability/node changes.
