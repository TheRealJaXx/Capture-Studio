package com.example.ui

import android.content.Context
import android.content.SharedPreferences

const val LEGAL_TERMS_VERSION = 1

private const val PREFS_NAME = "capture_studio_legal"
private const val KEY_EULA_ACCEPTED = "eula_accepted"
private const val KEY_EULA_ACCEPTED_VERSION = "eula_accepted_version"
private const val KEY_EULA_ACCEPTED_AT_MILLIS = "eula_accepted_at_millis"

fun isEulaAccepted(context: Context): Boolean {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val accepted = prefs.getBoolean(KEY_EULA_ACCEPTED, false)
    val version = prefs.getInt(KEY_EULA_ACCEPTED_VERSION, 0)
    return accepted && version >= LEGAL_TERMS_VERSION
}

fun acceptEula(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(KEY_EULA_ACCEPTED, true)
        .putInt(KEY_EULA_ACCEPTED_VERSION, LEGAL_TERMS_VERSION)
        .putLong(KEY_EULA_ACCEPTED_AT_MILLIS, System.currentTimeMillis())
        .apply()
}

val LEGAL_TERMS_TEXT = """
END USER LICENCE AGREEMENT — CAPTURE STUDIO
Version 1 — Effective [DATE]

PLEASE READ THIS AGREEMENT CAREFULLY. BY TAPPING "I ACCEPT" OR BY INSTALLING OR USING THE APP YOU AGREE TO BE BOUND BY IT. IF YOU DO NOT AGREE, TAP "DECLINE" AND DO NOT USE THE APP.

1. PARTIES AND DEFINITIONS
This agreement is between you ("you" or "the User") and [DEVELOPER / TRADING NAME] ("the Developer", "we", "us"). "The App" means the Capture Studio application, including all code, interfaces, designs, graphics, text, protocols, layouts, updates and documentation. "Content" means any video, audio, images, overlays, recordings, streams, text or other material that is captured, processed, displayed, transmitted, recorded or created using the App.

2. LICENCE
We grant you a limited, non-exclusive, non-transferable, non-sublicensable, revocable licence to install and use the App on devices you own or control, for your own personal or internal business use, subject to this agreement. The App is licensed to you, not sold. No ownership rights pass to you.

3. OWNERSHIP AND INTELLECTUAL PROPERTY
The App, its name, logos, look and feel, source code, object code, user interface, shortcut layouts, network protocols, algorithms and all related intellectual property rights are and remain the exclusive property of the Developer (and its licensors). All rights not expressly granted to you are reserved. Nothing in this agreement transfers any intellectual property rights to you. If you give us feedback, suggestions or ideas about the App, you grant us a perpetual, worldwide, royalty-free, irrevocable licence to use them without restriction or compensation.

4. RESTRICTIONS
You may not, and may not allow anyone else to: (a) copy, modify, adapt, translate or create derivative works of the App; (b) reverse engineer, decompile, disassemble or attempt to derive the source code, protocols or algorithms of the App, except to the extent this restriction is prohibited by law; (c) sell, rent, lease, lend, sublicense, distribute or otherwise make the App available to any third party; (d) remove or alter any proprietary notices; (e) use the App to build a competing product or service; (f) circumvent any technical limitation or security feature; (g) use the App for any unlawful purpose or in breach of this agreement.

5. YOUR CONTENT AND YOUR RESPONSIBILITY
You retain ownership of Content you create. You are SOLELY responsible for all Content you capture, view, stream, record, overlay, transmit or store using the App, and for how, where and to whom you share it. Without limitation, you are responsible for: (a) holding all rights, licences and permissions for the Content, including copyright and rights of game publishers, broadcasters, performers and other third parties; (b) obtaining consent of anyone who is recorded or streamed, and complying with all privacy, data-protection, recording, broadcasting, intellectual-property and other laws that apply to you; (c) complying with the terms of any platform, service, console, game or hardware manufacturer you use with the App; (d) not capturing or distributing copy-protected material where this is not permitted. The Developer does not host, monitor, review, endorse or control Content and accepts no responsibility or liability for any Content or for any claim, penalty, takedown, account suspension or ban arising from it.

6. PRIVACY AND PERMISSIONS
The App is designed to operate on your devices and your local network and may request permissions for camera, microphone, USB devices, storage and network access so that its features can work. [DEVELOPER TO CONFIRM: The Developer does not intentionally collect, store or sell your personal information or Content.] You are responsible for the security of your devices, networks and pairing codes. If we change how the App handles personal information we will update this agreement and our privacy notice, and comply with applicable law including the Protection of Personal Information Act 4 of 2013 (POPIA).

7. THIRD-PARTY HARDWARE, SERVICES AND PLATFORMS
The App interoperates with third-party hardware (including USB capture cards, cameras, audio interfaces and display devices) and third-party platforms and networks. We do not manufacture or control these third-party products and services. We do not warrant that the App will be compatible with any specific hardware, firmware version or operating system. You use third-party hardware, networks and platforms at your own risk.

8. "AS IS" AND NO WARRANTIES
TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE APP IS PROVIDED "AS IS" AND "AS AVAILABLE", WITH ALL FAULTS AND WITHOUT WARRANTY OF ANY KIND. WE EXPRESSLY DISCLAIM ALL WARRANTIES, EXPRESS, IMPLIED OR STATUTORY, INCLUDING IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, ACCURACY, TITLE, QUIET ENJOYMENT AND NON-INFRINGEMENT. WE DO NOT WARRANT THAT THE APP WILL MEET YOUR REQUIREMENTS, BE UNINTERRUPTED, SECURE OR ERROR-FREE, THAT LATENCY WILL BE WITHIN ANY SPECIFIC LIMIT, OR THAT DEFECTS WILL BE CORRECTED.

9. LIMITATION OF LIABILITY
TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW:
(a) IN NO EVENT SHALL THE DEVELOPER, ITS DIRECTORS, EMPLOYEES, AGENTS OR LICENSORS BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES, OR FOR ANY LOSS OF PROFITS, DATA, USE, GOODWILL, REVENUE, OPPORTUNITY OR HARDWARE DAMAGE, ARISING OUT OF OR IN CONNECTION WITH THE APP OR THIS AGREEMENT, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES;
(b) IN NO EVENT SHALL THE DEVELOPER'S TOTAL AGGREGATE LIABILITY ARISING OUT OF OR IN CONNECTION WITH THE APP OR THIS AGREEMENT EXCEED THE AMOUNT ACTUALLY PAID BY YOU FOR THE APP (IF ANY). AS THE APP IS PROVIDED FREE OF CHARGE, OUR TOTAL AGGREGATE LIABILITY IS ZERO;
(c) THIS LIMITATION APPLIES REGARDLESS OF THE THEORY OF LIABILITY (CONTRACT, DELICT, TORT, STRICT LIABILITY OR OTHERWISE).

10. INDEMNIFICATION
You agree to indemnify, defend and hold harmless the Developer and its affiliates, officers, directors, employees and agents against any claims, liabilities, damages, losses, fines, penalties and expenses (including legal fees) arising out of or related to: (a) your use or misuse of the App; (b) any Content you capture, process, stream, record or transmit; (c) your violation of this agreement; (d) your violation of any third-party right, including intellectual property, privacy or publicity rights; or (e) your violation of any applicable law or regulation.

11. TERMINATION
This agreement is effective until terminated. Your rights under this licence terminate automatically without notice if you fail to comply with any of its terms. Upon termination, you must stop using the App and uninstall all copies from your devices. The provisions of sections 1, 3, 4, 5, 8, 9, 10, 12 and 13 survive any termination.

12. GOVERNING LAW AND JURISDICTION
This agreement is governed by and construed in accordance with the laws of South Africa, without regard to its conflict of law principles. Any dispute arising from or related to this agreement or the App shall be subject to the exclusive jurisdiction of the competent courts of South Africa.

13. GENERAL
If any provision of this agreement is held invalid or unenforceable, that provision will be modified to the minimum extent necessary or severed, and the remaining provisions will continue in full force and effect. Our failure to enforce any right or provision will not be deemed a waiver. This agreement constitutes the entire agreement between you and the Developer regarding the App and supersedes all prior agreements.
""".trimIndent()
