# mc-mol-viewer

Avogadro などのデスクトップ分子ビューアを参考に、ORCA などの計算で得た
有限分子・錯体の構造を Minecraft Paper 上で眺めるビューアです。
バニラクライアントとリソースパックで球・結合・ラベルを表示する予定です。

周期結晶・単位胞・配位多面体などの CIF 表示は、別プロジェクトの `mc-cif-viewer` が担当します。

## 現在

独立した Gradle プロジェクトの骨組みです。MOL パーサーや表示機能はまだありません。

## ビルド

Java 25 と Gradle 9.8.0 を使用します。

```bash
./gradlew build
```

生成されるプラグイン JAR:

```text
build/libs/mc-mol-viewer-<version>.jar
```

## データ配置予定

```text
plugins/McMolView/molecules/
```

## 予定機能

- ORCA output の最終構造、XYZ、MDL MOL V2000 / V3000、SDF の読み込み
- 球棒・空間充填表示、原子ラベル
- 結合次数（MOL/SDF）、選択、回転、距離・角度測定
- ORCA の構造最適化ステップ / 複数構造の表示
- 複数分子の共有表示
