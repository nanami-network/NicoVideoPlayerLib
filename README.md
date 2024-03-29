# NicoVideoPlayerLib
[LavaPlayer](https://github.com/sedmelluq/lavaplayer) でニコニコ動画を再生させるためのライブラリ
[![](https://jitpack.io/v/nanami-network/NicoVideoPlayerLib.svg)](https://jitpack.io/#nanami-network/NicoVideoPlayerLib)

## 開発環境
- Java [16]

## 使い方
- 1. 必ず先にLavaPlayerを動作可能な状態にしてから実行してください
- 2. 次のコードを実行してください

````
     public static final AudioPlayerManager manager = new DefaultAudioPlayerManager();
     /* 中略 */
        
     manager.registerSourceManager(new NicoVideoAudioSourceManager());
````

**注意事項**
- 必ず先に NicoVideoAudioSourceManagerを登録してから

*{``AudioSourceManagers#registerLocalSource``}

*{``AudioSourceManagers#registerRemoteSources``}

などを登録してください


## サポートしている再生方法
- ニコ動 (HLS)
- ニコ動 (HLS with AES) (※AES-128によって保護されている動画)

## TODO:
- [ ] ニコ生に対応
