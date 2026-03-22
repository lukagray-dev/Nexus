# Instagram Monitor

Monitor Instagram messages, stories, and activity through modded app integration.

## Overview

This feature provides visibility into Instagram activity via a modified Instagram build with local database access and IPC bridge to Nexus Agent.

## Architecture

**Data Flow:**
```
Modded Instagram → Local IPC → Nexus Agent → Parent Dashboard
```

**Capabilities:**
- Direct messages (DMs)
- Story views and interactions
- Post activity and comments
- Following/followers changes
- Search history
- Media cache access

## Implementation Status

**Difficulty:** Moderate  
**Stability:** Moderate  
**Current Status:** Placeholder (Implementation Pending)

## Requirements

### Child Device
- Original Instagram uninstalled
- Modded Instagram from Nexus Agent catalog
- Nexus Agent with proper permissions

### Parent Dashboard
- Active connection to child device
- Instagram monitoring enabled

## Data Structure

```javascript
{
  type: "INSTAGRAM_MESSAGE",
  messageId: "string",
  threadId: "string",
  sender: "string",
  content: "string",
  timestamp: number,
  mediaType: "text|image|video|story",
  mediaUrl: "string"
}
```

## Security & Privacy

- Sandbox-isolated operation
- Local IPC only (no external network)
- Signature verification required
- Parental supervision use only

## Limitations

- Meta apps sensitive to tampering
- Higher risk of detection/account action
- Requires modded app installation
- Manual updates only

## Legal Notice

Designed exclusively for lawful parental supervision of minor children. Unauthorized surveillance prohibited.
