# R8 の keep ルール（release ビルド用）。実機で失敗を観測したものだけを、観測内容とともに置く。

# Glance のウィジェット更新は WorkManager を使う。WorkManager は androidx.work.OverwritingInputMerger を
# リフレクション（Class.newInstance）で生成するが、WorkManager 同梱のルール（-keep class * extends androidx.work.InputMerger）は
# メンバーを指定していないため、AGP 9 の既定（android.r8.strictFullModeForKeepRules=true）では引数なしコンストラクタが残らない。
# 観測: release ビルドでウィジェットが読み込み中のまま更新されず、logcat に
#   WM-InputMerger: java.lang.InstantiationException: java.lang.Class<androidx.work.OverwritingInputMerger> has no zero argument constructor
-keep class * extends androidx.work.InputMerger { <init>(); }
