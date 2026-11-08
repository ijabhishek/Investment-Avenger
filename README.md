# 📈 INVESTMENT AVENGER

**A private, all-in-one investment portfolio tracker for Android** — track Indian stocks, US stocks, mutual funds, ETFs and global assets in one place, with live prices from Yahoo Finance, profit/loss analytics, a built-in notepad, daily quotes and an optional app lock.

> **Idea:** X-Men Laxman  ·  **Built into reality by:** Abhishek

| | |
|---|---|
| App name | INVESTMENT AVENGER |
| Package | `com.example.portfoliopilot` |
| Version | 2.0 (versionCode 2) |
| Platform | Android 8.0+ (minSdk 26), compile/target SDK 35 |
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Data storage | On the phone only (Jetpack DataStore) – no account, no cloud |
| Market data | Yahoo Finance (free, unofficial, no API key) |

---

## 1. What the app does

| Area | Highlights |
|---|---|
| **Dashboard** | Greeting by time of day (*Good morning / afternoon / evening, \<name\>*), quote of the day with Copy & Share, total value and profit/loss for **India (₹)**, **USA ($)** and **Global ($)**, realized profit, asset allocation, top 5 holdings. |
| **Portfolio** | All holdings with units, average cost, invested amount, live market price, value and profit/loss. Buy more, sell, update price (manual holdings), purchase and sale history with notes. Filter by market. |
| **Add investment** | **a. Manually add** (India / USA / Global, any category name you type) or **b. Fetch from Yahoo** (search by company/fund name or code → name, code, currency and live price are filled in automatically; you only choose the quantity). |
| **Market** | Live watchlist (indices, top Indian and US stocks) and **Yahoo Finance search of any company** with its live price and day change. |
| **Analytics** | Profit/loss snapshot per market, best and worst performer, return % per holding, a breakdown donut chart for **each market** (India, USA, Global), capital-vs-value bars per market, win rate, recent sales. |
| **Profile** | Edit name/photo, quote with Copy/Share/New, **Notepad**, **Share Report** (text + CSV), themes, 14 accent colors, 8 fonts, text size, daily quote notification, app lock. |
| **Footer** | Fancy credit to the idea (X-Men Laxman) and the maker (Abhishek), plus Einstein's compound-interest quote. |

### Key rules (how the numbers work)
- **Average-cost method.** Buying more recalculates a *weighted average cost*. Selling realises profit against that average cost; the average of the units you keep does not change.
- **Profit/loss is measured against the market price**, never against your last purchase price. Buying more shares **below** the current market price therefore does **not** create a fake loss — a loss appears only when the market price falls below your average cost.
- **No currency mixing.** ₹ and $ are never added together. Each market has its own totals; the USD/INR rate is used only to rank *Top Holdings*.

---

## 2. Screens and features in detail

### 2.1 Start-up
1. **System splash** – navy background with the *full* logo (inset so the circular mask does not crop the text).
2. **In-app splash (5 seconds)** – animated logo, "INVESTMENT AVENGER" and the quote of the day.
3. **First run only:** a welcome dialog asks for your name (and optional photo).
4. If **App lock** is on, the unlock prompt (fingerprint / face / phone PIN) appears next.

### 2.2 Adding an investment (top-left **+** button)
- **a. Manually add** – name, ticker/scheme code, market (**India / USA / Global**), asset category (type any name or tap a suggestion: Stock, Mutual Fund, ETF, Gold, Crypto, Bond, Real Estate), quantity, purchase price, charges, optional notes. India is in ₹; USA and Global are in $.
- **b. Fetch from Yahoo** – type at least 2 letters; results appear after a short pause. Tap a result → name, code, exchange, currency and **live price** are filled. Enter the quantity (price stays editable). For USD assets you can switch **Track as Global asset**. Only INR and USD instruments are supported.

### 2.3 Buying more
Opens with the **current market price fetched live** (for holdings linked to Yahoo, or found automatically for Indian `.NS`/`.BO` and US tickers). The dialog previews your **new average cost** and the **profit/loss at market price** before you confirm.

### 2.4 Selling
Enter quantity, selling price and charges. The preview shows net proceeds, cost (units × average cost) and realized profit/loss. Selling everything removes the holding; the trade stays in history.

### 2.5 Notes on trades
The optional note typed while buying or selling is shown on the holding card (latest 5), in **History** (📝), and in the reports.

### 2.6 Notepad (Profile tab)
- List with search, newest first; **New note** button.
- Editor: title + content, **B** (bold), **I** (italic), Copy, Save. Formatting is stored as `**bold**` and `__italic__`; while editing the markers are dimmed and the text is shown styled.
- View mode: rendered note with Edit, Copy, Share and Delete (with confirmation).
- Leaving the editor with the back button saves automatically (like Google Keep).

### 2.7 Share Report (Profile tab)
- **Share text report** – readable summary through WhatsApp, e-mail or any app.
- **Export portfolio (.csv)** – holdings, purchase history and sales history (opens in Excel/Sheets).

### 2.8 Appearance
- **Themes:** System, Light, Dark, Black & White (OLED), **Retro** (old-time parchment look with a typewriter font).
- **Accent colors (14):** Emerald, Blue, Purple, Gold, Ruby, Teal, Orange, Rose, Indigo, Cyan, Lime, Coffee, Slate, Monochrome. The accent tints buttons, cards and surfaces.
- **Fonts (8):** Default, Poppins, Ubuntu, PT Serif, Courier Prime, Typewriter (Special Elite), Handwritten (Patrick Hand), Script (Pacifico – headings only). Bundled in the app, so they work offline.
- **Text size:** Auto (fits the screen width), Small, Normal, Large, Extra large. Layouts re-arrange (cards wrap, buttons flow to a new line, labels stay aligned) instead of overflowing when the phone's font/display size changes.

### 2.9 Daily quote notification
A quote notification (title *"💡 Quote of the day"*) is delivered every day at the hour you choose (7 AM, 9 AM, 12 PM, 6 PM, 9 PM), **even when the app is closed**. Android 13+ asks for notification permission once. The system may delay delivery by a few minutes to save battery.

### 2.10 Privacy & security
- **App lock** (optional): fingerprint, face or the phone's PIN/pattern/password. Also hides the app in the recent-apps list and blocks screenshots. Re-locks after 30 seconds in the background.
- Backups are disabled; no cloud, no account, HTTPS only.
- Release builds are shrunk and obfuscated (R8).

---

## 3. Install and run

### Requirements
- Android Studio (latest stable), **JDK 17**, Android SDK **Platform 35**
- A phone or emulator with **Android 8.0 (API 26) or newer**
- Internet access for Yahoo Finance (the app works offline with the last saved prices)

### Steps
1. Unzip the project and open the folder in Android Studio.
2. Wait for **Gradle sync** (downloads dependencies).
3. Select the `app` run configuration and press **Run ▶**.
4. Allow notifications when asked (Android 13+).

### Release build (optional)
1. *Build → Generate Signed App Bundle / APK* and create your own keystore.
2. Choose the **release** variant (R8 shrinking and obfuscation are enabled).
3. **Keep your keystore and its passwords safe** — you need the same keystore for every update.

---

## 4. Project structure

```
InvestmentAvenger/
├─ README.md                    ← this file
├─ DOCUMENTATION.md             ← full technical documentation
├─ build.gradle.kts, settings.gradle.kts, gradle.properties
└─ app/
   ├─ build.gradle.kts          ← dependencies, R8 release settings
   ├─ proguard-rules.pro        ← keep rules (serialization, WorkManager)
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/example/portfoliopilot/
      │   ├─ MainActivity.kt         activity, splash install, edge-to-edge, secure flag
      │   ├─ AppShell.kt             splash, lock screen, top bar, bottom navigation, dialogs host
      │   ├─ PortfolioViewModel.kt   state, storage, buy/sell logic, reports
      │   ├─ Models.kt               data classes, formatting helpers, markets
      │   ├─ YahooFinance.kt         search + live quotes
      │   ├─ AppTheme.kt             colors, retro theme, fonts, text scale
      │   ├─ Components.kt           shared UI pieces, clipboard/share, profile photo
      │   ├─ DashboardScreen.kt      PortfolioScreen.kt   MarketScreen.kt
      │   ├─ AnalyticsScreen.kt      ProfileScreen.kt     NotepadScreen.kt
      │   ├─ Dialogs.kt              profile, buy, sell, update-price dialogs
      │   ├─ QuoteNotifications.kt   daily notification (WorkManager)
      │   ├─ AppLock.kt              biometric / device-credential prompt
      │   └─ Quotes.kt               158 finance quotes + quote of the day
      └─ res/  drawable (logo, GIF, icons) · font (8 families) · mipmap-anydpi-v26 · values · xml
```

---

## 5. Technology

| Purpose | Library |
|---|---|
| UI | Jetpack Compose BOM 2024.12.01, Material 3, Material Icons Extended |
| State / lifecycle | ViewModel, Kotlin coroutines 1.9.0 |
| Storage | DataStore Preferences 1.1.1 + kotlinx.serialization JSON 1.7.3 |
| Network | OkHttp 4.12.0 (Yahoo Finance) |
| Background work | WorkManager 2.9.1 (daily quote) |
| Security | AndroidX Biometric 1.1.0, Fragment KTX 1.8.5 |
| Splash | AndroidX Core SplashScreen 1.0.1 |

Fonts (OFL / Apache / UFL licensed): Poppins, Ubuntu, PT Serif, Courier Prime, Special Elite, Patrick Hand, Pacifico.

---

## 6. Limitations (please read)
- Yahoo Finance is a **free, unofficial** source: quotes may be delayed, rate-limited or unavailable. The app shows a message instead of fake prices.
- **Not financial advice** and not for trade execution.
- Yahoo search supports **INR and USD** instruments only.
- Manually added holdings are **not** auto-updated — use *Update Price*.
- Brokerage charges are **not** included in the average cost (they are shown in total cost and reports).
- Data lives only on this phone: uninstalling the app or clearing its data deletes it. Use **Export (.csv)** regularly as your backup.
- Daily notification time is approximate (Android battery optimisation).
- No tax-lot (FIFO/LIFO) accounting, dividends, splits or multi-currency conversion.

## 7. Troubleshooting
| Problem | What to do |
|---|---|
| Search or prices do not load | Check internet; tap refresh on the Market tab; try again later (Yahoo may be throttling). |
| A holding shows "Manual price" | It was added manually. Use **Update Price**, or add it again with *Fetch from Yahoo*. |
| No daily notification | Profile → turn on *Daily quote notification*; allow notifications in phone Settings → Apps → Investment Avenger; disable battery restrictions for the app. |
| App lock cannot be enabled | Set a screen lock (PIN/pattern/fingerprint) in phone settings first. |
| Gradle sync fails | Use JDK 17 and install SDK Platform 35; check internet. |

## 8. Credits
- **Idea:** X-Men Laxman
- **Developed by:** Abhishek
- Market data: Yahoo Finance · Fonts: Google Fonts / Ubuntu Font Licence
