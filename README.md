<div align="center">
<h1>Brief You</h1>
<p><b>A calm, private RSS reader for Android with on-device AI.</b><br/>
A fork of <a href="https://github.com/Ashinch/ReadYou">Read You</a>.</p>
<p><a href="../../releases/latest"><b>Download the latest APK</b></a></p>
</div>

Brief You keeps everything that makes Read You great — the Material You design, Reeder-style reading, and support for FreshRSS, Miniflux, Fever, Google Reader API services and more — and adds a private AI layer that runs **entirely on your phone**. Articles, questions and instructions are never sent to a server: the AI is a Gemma model running through Google's open-source [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM).

Brief You installs **alongside** Read You (its own app ID, `io.github.humorist2601.briefyou`), so you can try it without losing anything. Move your feeds over with an OPML export/import.

## What's new compared to Read You

### On-device AI
- **Article summaries** — tap ✨ in the reader for 3–5 key points and a "Why it matters" line. The sheet opens above half height and grows smoothly as the summary is written.
- **Saved summaries** — a summary opens instantly the second time, articles with a summary show a ✨ next to their title in the list, and the summary is deleted together with its article (for example by *Keep archived articles*).
- **Group gists** — tap ✨ on any article list (a group like *News*, a feed, or everything) for a briefing of the newest 40 articles, grouped by story. One tap marks all of them as read.
- **Read aloud** — any summary or gist can be read aloud.

### Calmer feeds
- **One digest notification** ("12 new articles · From 5 feeds") instead of one notification per article grouped by site. It is posted once, a couple of seconds after a sync finishes, so everything arrives together. It lists one line per feed (for example `Feed name (3) · latest title`), so busy feeds never push the others out.
- **Reading time** — article items in the list show an estimate like `2h · 4 min read` next to the time. Feeds that only send a short teaser show no estimate rather than a wrong one.
- **Muted keywords** — unread articles mentioning them are marked read after each sync.
- **Duplicate stories** — when several feeds publish the same story, only the first stays unread.
- **Recently filtered** — the Filters page lists everything that was hidden and why (which word, or which story it duplicated). Tap to open it, or *Undo* to bring it back and stop it being filtered again. Entries are removed together with their articles.

### Feeds that look like themselves
- **Feed accent colors** — feed names in the article list take a muted accent from the feed's icon (or emoji), harmonized with your Material You theme.
- **Custom feed icons** — in a feed's options, tap its icon to use an emoji, an image from your phone, or the website icon again. Custom icons survive syncs.

### Settings
- **Settings → AI model**: import or remove the model, and edit the exact instructions given to the model for summaries and gists.
- **Settings → Filters**: muted keywords, duplicate-story hiding, and the recently filtered list.

### Look and feel
- **Google Sans Flex Rounded** throughout the app, and **Literata** — Google's serif designed for long reading — as a reading font option.
- **Material 3 Expressive**: shape-morphing buttons, shaped and tinted settings icons, the Expressive pull-to-refresh and loading indicators, and wavy progress bars.
- **Predictive back** gestures, including the back-to-home preview.

### Releases
- APKs are built for 64-bit ARM phones (arm64-v8a), which covers practically every phone that can run the on-device model.
- Every change is built by GitHub Actions and published to [Releases](../../releases) as **Brief You 0.16.2+N**, where `0.16.2` is the app version and `N` is a rising build number, so each APK installs as an update (works with updaters like Obtainium). The release tag uses the same `0.16.2+N` format.

## Setting up the AI

1. Open **Settings → AI model** and tap **Download Gemma 4 E2B**. On Hugging Face, download `gemma-4-E2B-it.litertlm` (about 2.6 GB).
2. Back in the app, tap **Import model file** and pick the download. It is copied into the app once; you can delete the original afterwards.
3. Tap ✨ on any article. The first answer takes a few seconds while the model loads; the model unloads from memory after a few idle minutes.

## Credits and license

Brief You is free software under the **GNU GPL 3.0**, like Read You. All credit for the original app goes to [Ashinch](https://github.com/Ashinch) and the Read You contributors. Gemma is provided under Google's Gemma terms; Brief You does not include the model, you download it yourself.

<details>
<summary><b>Original Read You README</b></summary>

<div align="center">
    <img width="200" height="200" style="display: block; border: 1px solid #f5f5f5; border-radius: 9999px;" src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/icon.png">
</div>

<br>
<br>
<br>

<div align="center">
    <img alt="GitHub" src="https://img.shields.io/github/license/ReadYouApp/ReadYou?color=c3e7ff&style=flat-square">
    <a target="_blank" href="https://github.com/ReadYouApp/ReadYou/releases">
        <img alt="Version" src="https://img.shields.io/github/v/release/ReadYouApp/ReadYou?color=c3e7ff&label=version&style=flat-square">
    </a>
    <img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/ReadYouApp/ReadYou?color=c3e7ff&style=flat-square">
    <br>
    <a target="_blank" href="https://t.me/ReadYouApp">
        <img alt="Telegram" src="https://img.shields.io/badge/Telegram-ReadYouApp-c3e7ff?logo=telegram&style=flat-square">
    </a>
    <a target="_blank" href="https://www.figma.com/file/ViBW8GbUgkTMmK6a80h8X1/Read-You?node-id=7028%3A23673">
        <img alt="Figma" src="https://img.shields.io/badge/Figma-ReadYou-c3e7ff?logo=figma&style=flat-square">
    </a>
</div>

<div align="center">
    <h1>Read You</h1>
    <p>An Android RSS reader presented in <a target="_blank" href="https://m3.material.io/">Material You</a> style.</p>
    <p>English&nbsp;&nbsp;|&nbsp;&nbsp;
    <a target="_blank" href="https://github.com/ReadYouApp/ReadYou/blob/main/README-de.md">Deutsch</a>&nbsp;&nbsp;|&nbsp;&nbsp;
    <a target="_blank" href="https://github.com/ReadYouApp/ReadYou/blob/main/README-zh-CN.md">简体中文</a>&nbsp;&nbsp;|&nbsp;&nbsp;
    <a target="_blank" href="https://github.com/ReadYouApp/ReadYou/blob/main/README-zh-TW.md">繁體中文 (Outdated)</a>&nbsp;&nbsp;|&nbsp;&nbsp;
    <a target="_blank" href="https://github.com/ReadYouApp/ReadYou/blob/main/README-fa.md">فارسی (Outdated)</a></p>
    <br/>
    <br/>
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/startup.png" width="19.2%" alt="startup" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/feeds.png" width="19.2%" alt="feeds" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/flow.png" width="19.2%" alt="flow" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/read.png" width="19.2%" alt="read" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png" width="19.2%" alt="settings" />
    <br/>
    <br/>
</div>

## Features

**Read You** is an Android RSS reader presented in [Material You](https://m3.material.io/) style.

The following are the progress made so far and the goals to be worked on in the near future:

- [x] Subscribe to RSS links
- [x] Import or export OPML files
- [x] Notification of new articles
- [x] Article readability optimization
- [x] Full content parse for original articles
- [x] Multi-account
- [x] Read aloud
- [ ] Android widget
- [ ] ...

## Integration

**Read You** integrates with some of third-party service APIs to support you in using your existing cloud accounts as data sources.

- [x] Fever
- [x] Google Reader
- [x] FreshRSS
- [ ] Miniflux
- [ ] Tiny Tiny RSS
- [ ] Inoreader
- [ ] Feedly
- [ ] Feedbin
- [ ] ...

## Download

[<img src="https://s1.ax1x.com/2023/01/12/pSu1a36.png" alt="Get it on GitHub" height="80">](https://github.com/ReadYouApp/ReadYou/releases)
[<img src="https://s1.ax1x.com/2023/01/12/pSnTZ0f.png"
     alt="Get it on Telegram Channel"
     height="80">](https://t.me/ReadYouApp)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/me.ash.reader/)

F-Droid releases can not upgrade to other releases and it is compiled, signed and distributed uniformly by [F-Droid](https://f-droid.org/docs/FAQ_-_General/).

## Nightly

We offer Nightly versions for testing purposes. These versions include the latest features and improvements but may not be as stable as our regular releases. Please note that Nightly builds might have bugs and are not recommended for everyday use.

You can download the Nightly version from the following link:

<a target="_blank" href="https://github.com/ReadYouApp/ReadYou/actions/workflows/build_commit.yaml">Check Nightly Builds</a>

Choose the latest workflow from the GitHub Actions page and download the artifacts from the workflow's summary page (You may require logging in to GitHub).

**Remember to back up your data before trying the Nightly version in case of any issues.**

## Sponsorship

**Read You** is a free open source software that benefits from the open source community and every user can enjoy it's full functionality for free, so if you appreciate my current work, you can buy me a cup of coffee.☕️

[<img src="https://s1.ax1x.com/2023/01/12/pSnHqpQ.png" alt="donate" height="80">](https://ash7.io/sponsor)

Thanks for all the love and support.❤️

## Localization

Thank you to each of the **Read You** translators, and if you would like to contribute, please submit a translation via [Weblate](https://hosted.weblate.org/engage/readyou/).

[<img src="https://hosted.weblate.org/widgets/readyou/-/horizontal-auto.svg" alt="" />](https://hosted.weblate.org/engage/readyou/)

## Build

> Welcome to open a [pull request](https://github.com/ReadYouApp/ReadYou/pulls). [GitHub Actions](https://github.com/ReadYouApp/ReadYou/actions) automatically packages all flavors of apk files for each commit.

**Read You** is based on [Jetpack Compose](https://developer.android.com/jetpack/compose) toolkit for building Android's native UI.

1. First you need to get the source code of **Read You**.

   ```shell
   git clone https://github.com/ReadYouApp/ReadYou.git
   ```

2. Then open it via [Android Studio (latest version)](https://developer.android.com/studio).

3. When you click the `▶ Run` button, it will be built and run automatically.

    > In case of lag, please select Release version build.

## Credits

### Open Source Projects

- [MusicYou](https://github.com/Kyant0/MusicYou)
- [ParseRSS](https://github.com/muhrifqii/ParseRSS)
- [Readability4J](https://github.com/dankito/Readability4J)
- [opml-parser](https://github.com/mdewilde/opml-parser)
- [compose-html](https://github.com/ireward/compose-html)
- [Rome](https://github.com/rometools/rome)
- [Feeder](https://gitlab.com/spacecowboy/Feeder)
- [Seal](https://github.com/JunkFood02/Seal)
- [news-flash](https://gitlab.com/news-flash)
- [besticon](https://github.com/mat/besticon)
- [Jiffy Reader](https://github.com/ansh/jiffyreader.com)
- ...

### Special Thanks

[<img src="https://avatars.githubusercontent.com/u/76829190?v=4" width="180" height="180" style="display: block; border: 1px solid #f5f5f5; border-radius: 9999px;"/>](https://github.com/Kyant0)

Thanks to **@Kyant0** for the design inspiration and Monet engine implementation for **Read You**.

[<img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.png" width="200" alt="Copyright © 2000-2023 JetBrains s.r.o. JetBrains and the JetBrains logo are registered trademarks of JetBrains s.r.o."/>](https://www.jetbrains.com/)

Thanks to **JetBrains** for allocating free open-source licences for IDEs for **Read You**.

[<img src="https://hosted.weblate.org/widgets/readyou/-/287x66-white.png"  width="200"/>](https://hosted.weblate.org/engage/readyou/)

Thanks to **Weblate** for providing free hosting of open source projects for **Read You**.

## License

GNU GPL v3.0 © [Read You](https://github.com/ReadYouApp/ReadYou/blob/main/LICENSE)

</details>
