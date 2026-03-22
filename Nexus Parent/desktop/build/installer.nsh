; Custom NSIS installer script for Nexus Parent
; This file is included in the main installer script

!macro customHeader
  !system "echo Nexus Parent Installer"
!macroend

!macro preInit
  ; Set default installation directory
  SetRegView 64
  WriteRegExpandStr HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation "$LOCALAPPDATA\Programs\${PRODUCT_NAME}"
  WriteRegExpandStr HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation "$LOCALAPPDATA\Programs\${PRODUCT_NAME}"
  SetRegView 32
  WriteRegExpandStr HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation "$LOCALAPPDATA\Programs\${PRODUCT_NAME}"
  WriteRegExpandStr HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation "$LOCALAPPDATA\Programs\${PRODUCT_NAME}"
!macroend

!macro customInstall
  ; Register custom protocol handler for deep linking
  WriteRegStr HKCU "Software\Classes\nexus" "" "URL:Nexus Protocol"
  WriteRegStr HKCU "Software\Classes\nexus" "URL Protocol" ""
  WriteRegStr HKCU "Software\Classes\nexus\DefaultIcon" "" "$INSTDIR\${PRODUCT_FILENAME},0"
  WriteRegStr HKCU "Software\Classes\nexus\shell\open\command" "" '"$INSTDIR\${PRODUCT_FILENAME}" "%1"'
  
  ; Create application data directory
  CreateDirectory "$APPDATA\Nexus Parent"
!macroend

!macro customUnInstall
  ; Remove custom protocol handler
  DeleteRegKey HKCU "Software\Classes\nexus"
  
  ; Optional: Remove application data (user can choose to keep it)
  ; RMDir /r "$APPDATA\Nexus Parent"
!macroend
