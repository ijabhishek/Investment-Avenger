# INVESTMENT AVENGER — Full Documentation

Version 2.0 · Android (Kotlin + Jetpack Compose) · Idea: X-Men Laxman · Built by Abhishek

---

## Table of contents
1. [Purpose and audience](#1-purpose-and-audience)
2. [User guide](#2-user-guide)
3. [Calculation rules](#3-calculation-rules)
4. [Architecture](#4-architecture)
5. [Data models and storage](#5-data-models-and-storage)
6. [Yahoo Finance integration](#6-yahoo-finance-integration)
7. [Theming, fonts and responsive text](#7-theming-fonts-and-responsive-text)
8. [Notifications](#8-notifications)
9. [Security and privacy](#9-security-and-privacy)
10. [Performance](#10-performance)
11. [Build, run and release](#11-build-run-and-release)
12. [Test checklist](#12-test-checklist)
13. [Known limitations](#13-known-limitations)
14. [Change history](#14-change-history)
15. [FAQ](#15-faq)

---

## 1. Purpose and audience
Investment Avenger lets one person keep a **private record of their investments** in several markets, see current value and profit/loss, review trades, write investment notes and receive a daily motivational quote. Everything is stored on the phone. It is meant for personal tracking, not for trading or advice.

## 2. User guide

### 2.1 Navigation
Bottom bar: **Dashboard · Portfolio · Market · Analytics · Profile**.
Top bar: **+** (add investment, top-left, hidden on Profile) · screen title · your profile photo (tap to edit name/photo).

### 2.2 First start
1. Wait for the 5-second splash (logo + quote).
2. Enter your name (photo optional) and tap **Save**.
3. Allow notifications (Android 13+) if you want the daily quote.

### 2.3 Add an investment
**Option a – Manually add**
1. Tap **+** → *a. Manually add*.
2. Fill name, ticker/scheme code, **market** (India ₹ / USA $ / Global $), **category** (type any name or tap a suggestion), quantity, purchase price, charges, notes.
3. **Confirm purchase**.

**Option b – Fetch from Yahoo**
1. Tap **+** → *b. Fetch from Yahoo*.
2. Type a company/fund name or code. Results (name, code, exchange, type) appear while you type.
3. Tap a result → the app shows the selected investment and its **live price**.
4. Enter the quantity; adjust the price if you paid something different; add notes.
5. **Confirm purchase**. The holding is now *live-linked*: its price refreshes automatically.

### 2.4 Buy more
Portfolio → holding → **Buy More**. The dialog shows the **current market price**, your average cost, then previews the **new average cost** and **profit/loss at market price**.

### 2.5 Sell
Portfolio → holding → **Sell**. Enter quantity, selling price, charges and notes. The preview shows net proceeds, cost and realized profit/loss.

### 2.6 Update price (manual holdings only)
Portfolio → holding → **Update Price**. Live-linked holdings do not show this button because the price is refreshed automatically.

### 2.7 Reading the Portfolio card
| Line | Meaning |
|---|---|
| Units | Quantity you still hold |
| Average cost | Weighted average price you paid |
| Invested | Units × average cost |
| Current market price | Live (Yahoo) or last manual price |
| Current value | Units × current market price |
| Coloured pill | Profit (green, +) or loss (red, −) with % |
| 📝 Notes | Notes typed when buying (latest 5) |

### 2.8 Market tab
- Watchlist of indices and large Indian/US stocks (loaded when you open the tab; refresh with the ↻ button).
- **Search box:** type any company, fund or index. Results come from Yahoo Finance with live price and day change. Watchlist matches are shown below.

### 2.9 Analytics tab
1. **Profit & loss snapshot** per market + realized profit.
2. **Best performer / Needs attention.**
3. **Return by holding** (bars, % of cost).
4. **Breakdown donut + legend for each market** that has holdings (🇮🇳 India, 🇺🇸 USA, 🌍 Global) in its own currency.
5. **Capital vs value** bars per market.
6. **Portfolio health** (win rate, buys, sells) and **recent sales**.

### 2.10 Profile tab
| Card | What it does |
|---|---|
| Greeting | Time-based greeting, your name, **Edit** |
| Financial wisdom | Quote with **Another quote**, **Copy**, **Share** |
| My Notepad | Opens the notepad |
| Share Report | **Share text report** and **Export portfolio (.csv)** |
| Theme | System / Light / Dark / Black & White / Retro + 14 accent colors |
| Font & text size | 8 fonts, 5 text sizes |
| Daily quote notification | On/off and time |
| Privacy & security | App lock on/off |
| Footer | Credits and Einstein's quote |

### 2.11 Notepad
- **List:** search, newest first, **New note**.
- **Editor:** Title, **B**, **I**, Copy, **Save**; select text then tap B or I (tap again to remove). Back button auto-saves.
- **View:** Edit, Copy, Share, Delete (asks confirmation).
- Notes are stored with the rest of your data on the phone.

## 3. Calculation rules

Let `u` = units, `a` = average cost, `p` = current market price.

| Quantity | Formula |
|---|---|
| Invested | `u × a` |
| Current value | `u × p` |
| Profit/Loss | `u × p − u × a` |
| Profit/Loss % | `Profit/Loss ÷ Invested × 100` |

**Buy more** (old units `u0`, old average `a0`, new units `u1`, price `p1`):

```
new units   = u0 + u1
new average = (u0 × a0 + u1 × p1) / (u0 + u1)
total cost  = u1 × p1 + charges        (charges are not part of the average)
```
Example: 10 units @ ₹100 then 10 @ ₹80 → average ₹90. If the market is ₹95, value = 20 × 95 = ₹1,900, invested = ₹1,800, **profit ₹100 (+5.56%)** — no loss, because the market price is above the average.

**Sell** (units `s`, price `q`, charges `c`):

```
cost basis   = s × a
net proceeds = s × q − c
realized P&L = net proceeds − cost basis
remaining    = u − s       (average cost stays a; holding removed when ≤ 0.00001)
```

**Current price rules**
- Live-linked holding: price = latest Yahoo price (start-up and every 5 minutes).
- Manual holding: price changes only through **Update Price**; buying more keeps the existing price unless a live price was found.
- New manual holding: current price = purchase price.

**Top holdings ranking:** by current value; USD values are multiplied by the live USD/INR rate (if unavailable, by 1) only for ordering.

**Markets and currencies:** India → INR (₹); USA and Global → USD ($). Totals are always kept per market.

## 4. Architecture

```
┌──────────────┐   state (Compose)   ┌─────────────────────┐
│ Screens (UI) │ ◄────────────────── │ PortfolioViewModel  │
│ Dashboard    │ ── user actions ──► │  holdings, purchases│
│ Portfolio    │                     │  sales, notes, prefs│
│ Market       │                     └───────┬─────────────┘
│ Analytics    │                             │
│ Profile      │            ┌────────────────┼──────────────────┐
│ Notepad      │            ▼                ▼                  ▼
└──────────────┘      DataStore (JSON)   YahooFinance      QuoteNotifier
                      saved on phone      (OkHttp, HTTPS)   (WorkManager)
```

- **Single activity** (`MainActivity`, a `FragmentActivity` because the biometric prompt needs it) hosting all Compose screens.
- **One ViewModel** holds all state as Compose `mutableStateOf`; screens read it and call its functions.
- **Saves** go through a conflated `Channel` consumed on an app-wide scope: the latest state always wins, writes are sequential, and a save is never lost when the screen closes.
- **Early returns in `PortfolioApp`:** splash → lock screen → notepad → main scaffold.
- Source size: about 4,300 lines of Kotlin in 17 source files.

### 4.1 Source files
| File | Responsibility |
|---|---|
| `MainActivity.kt` | Installs the system splash, edge-to-edge, `FLAG_SECURE` when app lock is on, re-lock after 30 s in background |
| `AppShell.kt` | `SplashScreen`, `LockScreen`, notification-permission prompt, `PortfolioApp` (top bar, bottom bar, dialogs) |
| `PortfolioViewModel.kt` | State, loading/saving, buy/sell, price refresh, notes, settings, text/CSV reports |
| `Models.kt` | `Holding`, `PurchaseTransaction`, `SaleTransaction`, `Note`, `MarketStats`, `MarketQuote`, Yahoo types, formatters, markets |
| `YahooFinance.kt` | `search`, `quote`, `quotes`, `searchWithPrices` |
| `AppTheme.kt` | Color schemes (accent, dark, B&W, Retro), fonts, typography, text scale, system-bar icon colors |
| `Components.kt` | `LabelValueRow`, `MetricCard`, `PnlChip`, `MarketSummaryRow`, `SectionCard`, `QuoteCard`, `AppFooter`, `ProfileAvatar`, clipboard/share, photo saving |
| `DashboardScreen.kt` | Dashboard and `HoldingSummaryCard` |
| `PortfolioScreen.kt` | Holdings, history, filters, sell/update dialogs host |
| `MarketScreen.kt` | Watchlist + Yahoo search |
| `AnalyticsScreen.kt` | Analytics cards and donut charts |
| `ProfileScreen.kt` | Profile tab sections |
| `NotepadScreen.kt` | Notepad list/view/edit and bold/italic markup |
| `Dialogs.kt` | Profile dialog, update price, buy (manual/Yahoo/buy more), sell |
| `QuoteNotifications.kt` | WorkManager scheduling and the notification |
| `AppLock.kt` | Biometric/device-credential prompt |
| `Quotes.kt` | 158 quotes, `dailyQuote()`, `quoteAsText()` |

## 5. Data models and storage

### 5.1 Models (JSON serialized, unknown fields ignored so older saves still load)
| Model | Fields |
|---|---|
| `Holding` | id, name, symbol, market, category, currency, units, averageCost, currentPrice, yahooSymbol (blank = manual) |
| `PurchaseTransaction` | id, holdingId, name, symbol, market, category, currency, units, price, charges, totalCost, date, time, notes |
| `SaleTransaction` | id, holdingId, name, symbol, market, category, currency, unitsSold, avgCostBasis, sellPrice, grossAmount, charges, netAmount, realizedPnl, realizedPnlPct, date, time, notes |
| `Note` | id, title, content (with `**bold**` / `__italic__` markers), timestamp, updatedAt |

### 5.2 DataStore (`portfolio_v13_prefs`)
| Key | Content |
|---|---|
| `holdings_json`, `purchases_json`, `sales_json`, `notes_json` | JSON lists |
| `user_name`, `profile_photo_uri` | Profile (photo is a private file path) |
| `theme_mode`, `accent_color`, `font_style`, `text_size` | Appearance |
| `notify_enabled`, `notify_hour`, `notify_asked` | Notification settings |
| `app_lock` | App lock on/off |

Each list is decoded independently, so one damaged list does not erase the others. Saving is skipped until loading has finished, so empty data can never overwrite saved data.

### 5.3 Files
- `filesDir/profile_<time>.jpg` – profile photo, resized to ≤ 1024 px; older photos are deleted.
- `cacheDir/portfolio_report.csv` – created on export and shared through `FileProvider`.

## 6. Yahoo Finance integration
All calls use HTTPS with a browser-like User-Agent and a cookie jar (one warm-up visit per session).

| Use | Endpoint |
|---|---|
| Live price, previous close, currency | `https://query1.finance.yahoo.com/v8/finance/chart/<symbol>?interval=1d&range=1d` |
| Search by name/code | `https://query2.finance.yahoo.com/v1/finance/search?q=<text>&quotesCount=12&newsCount=0` |
| USD→INR rate | chart for symbol `INR=X` |

- Up to **8 requests run in parallel**; timeouts 10 s.
- Search results are limited to Equity, ETF, Mutual fund and Crypto types.
- Symbols: Indian stocks use `.NS` (NSE) or `.BO` (BSE); the app shows the code without `.NS`.
- **Watchlist** (54 symbols): NIFTY 50, SENSEX, S&P 500, NASDAQ, 29 Indian stocks and 21 US stocks. Loaded only when the Market tab is opened or refreshed.
- **Holding refresh:** at start-up and every 5 minutes for Yahoo-linked holdings; failures keep the last saved price and show a status line.
- **Buy more lookup:** uses the saved Yahoo symbol or tries `<code>.NS`, `<code>.BO` (India) or `<code>` (USA); the result is accepted only when its currency equals the holding's currency.

## 7. Theming, fonts and responsive text
- **Colors:** every accent color generates a full Material 3 scheme (primary, containers, surfaces) in light and dark; *Monochrome* + dark uses the OLED Black & White scheme.
- **Retro:** parchment background, ink-brown text, rust accent; if the font is *Default* it switches to the typewriter font.
- **Fonts:** bundled TTF files in `res/font` (Poppins, Ubuntu, PT Serif, Courier Prime, Special Elite, Patrick Hand, Pacifico). *Script* is used for headings only.
- **Text size:** multiplies the system font scale. *Auto* = screen width ÷ 392 dp, limited to 0.88–1.15. The bottom navigation labels are capped at 1.15 and stay on one line.
- **Layout safety:** values sit in right-aligned weighted columns, button rows and chip groups use `FlowRow`, metric cards use equal heights with label on top and value at the bottom, so nothing drifts "up and down" at large font sizes.
- **System bars:** edge-to-edge with icon colors chosen from the theme (light icons on the dark splash).

## 8. Notifications
- `QuoteNotifier.schedule` enqueues a unique 24-hour `PeriodicWork` starting at the next chosen hour. Changing the hour re-times it; turning it off cancels it. Survives reboots (WorkManager).
- `DailyQuoteWorker` shows channel **Daily quote** with the quote of the day (`dailyQuote()`: one quote per calendar day, the same as the splash and dashboard). Tapping opens the app.
- Permission `POST_NOTIFICATIONS` (Android 13+) is requested once automatically and again when you switch the option on.

## 9. Security and privacy
| Measure | Detail |
|---|---|
| Local data only | No account, server, analytics or ads; only Yahoo Finance requests leave the phone |
| App lock | `BiometricPrompt` with *biometric (weak or strong) or device credential*; unlock on start; relock after > 30 s in background; if the phone's screen lock is removed the app does not lock you out |
| `FLAG_SECURE` | Active while app lock is on (hides recents thumbnail, blocks screenshots) |
| Backups | `allowBackup=false`, data-extraction rules exclude everything |
| Network | HTTPS only, cleartext disabled |
| Release hardening | R8 minify + resource shrinking, keep rules for serialization and the worker |
| Sharing | Reports leave the app only when you tap Share/Export; the CSV is shared via a read-only `FileProvider` URI |

No software can be made completely un-hackable; sign releases with your own keystore and keep the phone's OS updated.

## 10. Performance
- Market data downloaded **only when needed**; parallel requests (8 at a time).
- JSON encoding/decoding on background threads; saves coalesced.
- `remember`-ed aggregates (market totals, rankings) recompute only when holdings change; lists use stable keys.
- Profile photos resized once on import, not on every draw.
- R8 shrinks and optimises release builds.
- Splash GIF is the only large asset (≈1.4 MB).

## 11. Build, run and release
| Item | Value |
|---|---|
| Android Gradle plugin | see `build.gradle.kts` (root) |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Java / Kotlin JVM target | 11 |
| Permissions | `INTERNET`, `POST_NOTIFICATIONS` |
| Release | `isMinifyEnabled = true`, `isShrinkResources = true` |

Steps are in the README (§3). Update `versionCode`/`versionName` in `app/build.gradle.kts` for every new release.

## 12. Test checklist
- [ ] Fresh install: welcome dialog, name saved, dashboard greeting matches the time of day.
- [ ] Manual add India/USA/Global; custom category appears in Analytics.
- [ ] Yahoo add (e.g. an Indian stock and a US stock): name/code/price filled; price refreshes after restart.
- [ ] Buy more at a lower price than market → profit/loss **not** negative; average cost matches the formula.
- [ ] Sell part and all → realized P&L = net − units × average; holding removed at zero.
- [ ] Notes appear on holding card and in History.
- [ ] Analytics shows separate donuts for each market; every amount has exactly **one** currency symbol.
- [ ] Market search returns companies with prices; airplane mode shows a friendly message.
- [ ] Notepad: create, bold/italic, copy, share, edit, delete, search; back saves.
- [ ] Share text report and CSV open in other apps.
- [ ] Themes (incl. Retro), 14 accents, 8 fonts, 5 text sizes; large phone font size keeps cards aligned.
- [ ] Notification arrives at the chosen hour with the app closed.
- [ ] App lock: prompt on start and after 30 s in background; screenshot blocked.
- [ ] Profile photo survives app restart.

## 13. Known limitations
See README §6. Additionally: the daily notification uses WorkManager (not exact alarms); Yahoo data is unofficial and may change without notice; foreign-currency instruments (other than INR/USD) cannot be added through Yahoo.

## 14. Change history
| Version | Highlights |
|---|---|
| 2.0 | Average-cost method; live Yahoo add/search/refresh; buy-more live price and realistic profit/loss; notepad with bold/italic; Share Report; Global market; 14 accents, 8 fonts, Retro theme, text size; daily quote notification; app lock; per-market analytics redesign; fancy footer; splash icon and white-area fixes; code split into 17 source files, cleanup and R8 |
| 1.x | First versions: manual portfolio, trade history, analytics, market watchlist, profile |

## 15. FAQ
**Why does my holding not update?** It is a manual holding. Use *Update Price* or add it again via *Fetch from Yahoo*.

**Is my data safe if I uninstall?** No — data is local. Export the CSV first.

**Why average cost and not FIFO?** The app deliberately uses the average-cost method, as requested: simple and predictable.

**Why are brokerage charges not in the average?** They are recorded and included in the *total cost* and reports; the average is based on price × units only.

**Can I add a company from another country?** Only if it trades in USD (track it as Global) or INR. Other currencies are rejected.
