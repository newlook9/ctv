# Live TV - build progress

## Part 1 - DONE
- Crash guard + safe start (shows the error text on next launch)
- Permanent signing key support + GitHub release for in-app updates (workflow)
- In-app update check and one-tap install
- Whole app text in English (res/values/strings.xml)
- Channel scan: run once, results saved on the device (no constant background checking)
- Hide non-working channels, alternate-link fallback
- Slow-internet options: low start quality, data saver, audio only, 4K manual option
- Lite mode, auto country on first start, screen fit (Fit / Fill / Zoom)
- Radio card (logo, name, song title, animated bars) + radio keeps playing outside the app
- MP4 recording saved to the Gallery
- Start app on box boot (setting)
- Favorites, playlists, gallery files, themes, logos, focus highlight

## Part 2 - DONE
- Language picker + translations (English, Urdu, Arabic, Hindi, Bengali, Persian, Turkish, French, Spanish, Russian, Indonesian)
- Parental lock (PIN + locked categories)
- Backup / restore of favorites and playlists
- Remote number keys, recently watched, previous-channel button
- Sleep timer + record for a set time
- Favorites reorder + hide a channel
- Picture-in-picture (phone)

## GitHub secrets needed once (see GITHUB_SECRETS.txt, kept outside this zip)
KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS

## Part 3 - DONE
- World mode: all channels of the world in one list (TV and Radio), no country needed
- Search box searches the whole world list at once
- Scan builds a verified list on the device; "Verified only" switch in More menu
- Browse by: Country / Category / World

## Part 4 - DONE
- Scan button moved to the top right (next to the Live TV logo)
- Country and category lists have a search box inside the dropdown
- Channel search box moved up beside the dropdowns (more room for channels)

## Part 5 (this zip) - DONE
- Channel restarts by itself when the internet comes back
- Volume boost (+3 / +6 / +10 dB), sort (original / A-Z / verified first)
- Gallery videos and songs continue from the saved position
- Android box: long-press OK adds/removes a favorite
- Android box remote map:
  - CH+ / CH-, Media next / previous : next / previous channel
  - Play/Pause, Play, Pause, Stop     : control playback
  - FF / Rewind (and Left/Right in full screen) : seek 10 s for gallery files
  - Up/Down in full screen            : previous / next channel
  - OK in full screen                 : back to the list
  - Number keys                       : type a channel number
  - Volume + / - / Mute               : system volume
  - Menu                              : menu of the selected channel (or More menu)
  - Search                            : go to the search box
  - Guide                             : back to the list
  - Red: favorite, Green: quality, Yellow: record, Blue: More menu
  - Last channel key                  : previous channel

## Part 6 (this zip) - DONE
- Play / pause button added to the button bar (TV, radio and own files)
