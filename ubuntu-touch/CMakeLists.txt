cmake_minimum_required(VERSION 3.10)

# Identity comes from config/identity.env (single source of truth).
file(STRINGS "${CMAKE_CURRENT_SOURCE_DIR}/config/identity.env" _identity REGEX "^[A-Z_]+=")
foreach(_line IN LISTS _identity)
  string(REGEX MATCH "^([A-Z_]+)=(.*)$" _m "${_line}")
  set(${CMAKE_MATCH_1} "${CMAKE_MATCH_2}")
endforeach()

project(${CLICK_NAME} NONE)

# Clickable passes CLICK_ARCH for compiled builders; a pure-QML click is arch-independent.
if(NOT CLICK_ARCH)
  set(CLICK_ARCH "all")
endif()

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/build/payload/www/index.html")
  message(FATAL_ERROR "build/payload is missing: run ubuntu-touch/scripts/stage.sh first")
endif()

configure_file(click/manifest.json.in ${CMAKE_CURRENT_BINARY_DIR}/manifest.json @ONLY)

# For click packaging the install prefix is the click root (Clickable sets it).
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/manifest.json DESTINATION ${CMAKE_INSTALL_PREFIX})
install(FILES click/nooz.apparmor click/nooz.desktop DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY qml DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY build/payload/www build/payload/shims build/payload/assets DESTINATION ${CMAKE_INSTALL_PREFIX})
