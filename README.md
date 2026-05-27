# Common Android Utilities for Splunk Observability

## Documentation
- [Install the Splunk RUM Android Agent](https://help.splunk.com/en/splunk-observability-cloud/manage-data/instrument-front-end-applications/instrument-mobile-and-web-applications-for-splunk-real-user-monitoring-rum/instrument-android-applications-for-splunk-rum/splunk-rum-android-agent-version-2.0.0-and-above/install-the-splunk-rum-android-agent)

## Overview
This repository contains shared Android utilities, common components, and internal infrastructure used by the Splunk Android RUM Agent and Session Replay SDK. It provides reusable functionality that supports instrumentation, telemetry collection, configuration, and platform integrations across the Splunk Android Observability ecosystem.

## Repository Structure

The Session Replay SDK is part of the broader Splunk Android Observability ecosystem:

- [`splunk-otel-android`](https://github.com/signalfx/splunk-otel-android)  
  Core OpenTelemetry-based Android instrumentation and RUM SDK.

- [`splunk-session-replay-android`](https://github.com/signalfx/splunk-session-replay-android)  
  Session Replay SDK for capturing and replaying Android user sessions.

- [`splunk-common-android`](https://github.com/signalfx/splunk-common-android)  
  Shared Android components and utilities used across Splunk Android SDKs.