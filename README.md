# PROJECT BREAKLINE — Android native MVP

HTMLではなく、Android SDKの標準`View`とJavaだけで作ったネイティブ版です。

## 実装範囲

- 縦画面固定、左右ドラッグによる隊列移動
- 前方自動射撃、数字付き樽、4種の即時報酬
- 仲間減少、敵3段階の縦スライス、ボス戦、勝敗画面
- 3ステージ、端末内コイン、永続強化3項目
- `SharedPreferences` によるローカル保存
- 通信・ログイン・基地建設・PvP・広告・課金なし
- `INTERNET`権限なし（完全オフライン）

## 起動方法

1. Android Studioでこの`native-android`フォルダを開く
2. Android SDK Platform 35を選択してGradle Sync
3. Android端末またはエミュレータを縦画面で起動
4. `app`をRun

最低対応APIは26です。リリースビルドはAndroid Studioの`Build > Generate App Bundles or APKs`から作成できます。

## 重要な設計判断

この版はコアゲームプレイの検証を優先したネイティブ縦スライスです。キャラクター、道路、敵、VFXはコード描画なので、次工程で独自アートと音へ置き換えます。ゲーム状態とローカル保存をJava側に集約しているため、MVPの仕様確認後に素材だけを差し替えられます。
