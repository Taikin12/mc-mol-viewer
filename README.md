# mc-mol-viewer

Avogadro などのデスクトップ分子ビューアを参考に、ORCA などの計算で得た
有限分子・錯体の構造を Minecraft Paper 上で眺めるビューアです。
バニラクライアントとリソースパックで球・結合・ラベルを表示します。

周期結晶・単位胞・配位多面体などの CIF 表示は、別プロジェクトの `mc-cif-viewer` が担当します。

## 現在の機能

- 読み込み形式
  - XYZ（複数フレーム対応、結合は距離から推定）
  - MDL MOL V2000 / V3000
  - SDF（`$$$$` 区切りで複数構造 → フレーム）
  - ORCA 出力の最適化ステップ（`CARTESIAN COORDINATES (ANGSTROEM)` を全フレーム化、A.U. のみの場合は変換）
- 球棒表示（vdW半径の30%）/ 空間充填表示（vdW・共有結合半径、等倍）
- 結合次数 1〜3 の多重結合を平行な棒で表示
- 原子ラベル、原子選択による距離・角度・二面角の測定（3Dの線とラベル表示）
- モデルの回転・拡大縮小・原点移動、複数モデルの共有表示と共有編集
- 複数フレームの切り替え・自動再生（`/mol next`、`/mol prev`、`/mol play`、GUI、フレーム棒）
- フレーム一覧GUI（サムネイル一覧、速度、ループ、先頭/最後）
- リソースパックによる球・棒モデル（DyedItemColor で原子色を着色）

## ビルド

Java 25 と Gradle 9.8.0 を使用します。

```bash
./gradlew build
```

生成されるプラグイン JAR:

```text
build/libs/mc-mol-viewer-<version>.jar
```

テスト:

```bash
./gradlew test
```

## リソースパック

```bash
python3 resourcepack/build_pack.py
```

出力: `build/mcmolview-resourcepack.zip`（アイテムモデル `mcmolview:sphere` / `mcmolview:stick`）。
サーバーの `resource-pack` に配置するか、`.minecraft/resourcepacks` に入れて適用してください。

## サーバーへの配備

`mc-backend`（`mc` = 192.168.100.13）へ配備します。

```bash
bash scripts/deploy-main.sh            # ビルドして配備
bash scripts/deploy-main.sh --no-build # 既存JARを配備
```

- JAR を `plugins/` に、`samples/` を `plugins/McMolView/molecules/` にコピー
- 親プロジェクトの共有パック `viewers.zip` を再生成（CIF + Mol の両アセット）
- `server.properties` の `resource-pack` / `resource-pack-sha1` を更新して `mc-backend` を再起動

共有パックだけを更新する場合:

```bash
bash ../scripts/deploy-resourcepack.sh --restart
```

CIF と Mol は同じ `viewers.zip` を共有するため、片方の配備で他方のアセットが消えることはありません。

## データ配置

```text
plugins/McMolView/molecules/
```

`config.yml` の `sources` にディレクトリやファイルを追加できます。
`scan-depth`、`max-atoms`、`max-models`、`blocks-per-angstrom`、`atom-scale`、
`show-bonds`、`show-atom-labels`、`atom-colors` などを設定可能です。

`samples/` に water.xyz / water.mol / water.sdf / water_orca.out / benzene_opt.out（ORCA最適化10フレーム）があります。

## コマンド

```text
/mol list [page]            分子ファイル一覧
/mol view <file>            モデルを追加
/mol viewat <scale> <file>  倍率 (blocks/Å) を指定して追加
/mol next | /mol prev       構造フレームを移動
/mol frame <n>              構造フレームを指定
/mol play                   自動再生の開始 / 停止
/mol loop                   ループ再生の ON/OFF
/mol speed <ticks>          再生間隔 (2-40 ticks)
/mol frames                 フレーム一覧・再生 GUI
/mol models                 表示中モデル一覧
/mol select <id>            操作対象を選択
/mol remove <id>            モデル削除（所有者/管理者）
/mol tp <id>                モデルの近くへ移動
/mol share <id> on|off      共有編集の切り替え（所有者/管理者）
/mol clear | /mol clearall  選択中 / 自分のモデルを削除
/mol wand [menu|view|atom|frame]  操作棒を取得
/mol wands                  4種類の棒をまとめて取得
/mol pack                   リソースパックを再適用
/mol reload                 設定とファイル一覧を再読み込み
```

### 操作棒

- 分子メニュー棒: 右クリックで GUI。モデルをクリックで選択。
- 分子ビュー棒: 左クリック(空)で回転、左クリック(ブロック面)で回転軸変更、
  スニーク+左クリック(ブロック)で原点設定、スニーク+スクロールで全体サイズ。
- 分子原子棒: 右クリックで測定原子を最大4個選択、左クリックで解除。
- 分子フレーム棒: 右クリックで次の構造、左クリックで前の構造。
  スニーク+右クリックで最後、スニーク+左クリックで最初のフレーム。

## 制限・今後

- 空間充填表示では結合を描画しません（球に埋もれるため）。結合を見る場合は球棒表示を使ってください。
- 原子半径は Å と `blocks-per-angstrom` から計算します。`atom-scale` 1.0 が標準です。
- XYZ / ORCA 出力の結合次数は推定せず、すべて単結合です。次数が必要な場合は MOL/SDF を使ってください。
- ORCA 出力の各フレームに `FINAL SINGLE POINT ENERGY` があれば、情報表示にエネルギーと先頭フレームからの差 (kJ/mol) を出します。
- ORCA の振動計算など、座標ブロックが大量に出る出力はフレーム数が多くなります。
- 分子座標は重心を中心に配置するため、入力ファイルの絶対座標は表示位置に影響しません。
- 結合の描画は原子色を半分ずつ塗り分けます。
