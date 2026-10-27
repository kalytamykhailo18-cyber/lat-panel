<?php
// Demo catalog: only royalty-free / openly licensed content, used for the Google Play review
// and for showing the app without a provider account.
//
// - Movies: Blender Foundation open movies, Creative Commons Attribution (CC BY).
//   https://studio.blender.org/films/  Credit: (CC) Blender Foundation | www.blender.org
// - Live channels: public test streams published by Apple, Akamai, Mux and Unified Streaming
//   for player testing.
//
// For full control you can upload the movie files to this server and use relative paths
// (e.g. 'media/sintel.mp4'), which are resolved against the demo folder URL.

return [
    'live_categories' => [
        ['id' => '1', 'name' => 'Demo'],
    ],
    'live' => [
        ['id' => 1, 'category' => '1', 'name' => 'Big Buck Bunny 24/7', 'logo' => 'img/live1.png', 'url' => 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'],
        ['id' => 2, 'category' => '1', 'name' => 'Bip Bop Test', 'logo' => 'img/live2.png', 'url' => 'https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8'],
        ['id' => 3, 'category' => '1', 'name' => 'Señal de prueba', 'logo' => 'img/live3.png', 'url' => 'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8'],
        ['id' => 4, 'category' => '1', 'name' => 'Tears of Steel', 'logo' => 'img/live4.png', 'url' => 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8'],
    ],

    'vod_categories' => [
        ['id' => '10', 'name' => 'Películas abiertas'],
    ],
    'vod' => [
        ['id' => 101, 'category' => '10', 'name' => 'Big Buck Bunny', 'year' => '2008', 'duration' => '00:09:56', 'rating' => '7.2',
         'poster' => 'img/bbb.png', 'backdrop' => 'img/bbb_backdrop.jpg', 'ext' => 'mp4', 'url' => 'https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4',
         'plot' => 'Un conejo gigante se venga con humor de tres abusones del bosque. (CC) Blender Foundation.'],
        ['id' => 102, 'category' => '10', 'name' => 'Sintel', 'year' => '2010', 'duration' => '00:14:48', 'rating' => '7.5',
         'poster' => 'img/sintel.png', 'backdrop' => 'img/sintel_backdrop.jpg', 'ext' => 'mp4', 'url' => 'https://archive.org/download/Sintel/sintel-2048-surround.mp4',
         'plot' => 'Una joven solitaria busca al pequeño dragón que una vez rescató. (CC) Blender Foundation.'],
        ['id' => 103, 'category' => '10', 'name' => 'Elephants Dream', 'year' => '2006', 'duration' => '00:10:54', 'rating' => '6.5',
         'poster' => 'img/ed.png', 'backdrop' => 'img/ed_backdrop.jpg', 'ext' => 'mp4', 'url' => 'https://archive.org/download/ElephantsDream/ed_1024_512kb.mp4',
         'plot' => 'Dos personajes exploran un extraño mundo mecánico. La primera película abierta. (CC) Blender Foundation.'],
        ['id' => 104, 'category' => '10', 'name' => 'Tears of Steel', 'year' => '2012', 'duration' => '00:12:14', 'rating' => '6.9',
         'poster' => 'img/tos.png', 'backdrop' => 'img/tos_backdrop.jpg', 'ext' => 'm3u8', 'url' => 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8',
         'plot' => 'Un grupo de científicos en Ámsterdam intenta salvar al mundo de los robots. (CC) Blender Foundation.'],
    ],

    'series_categories' => [
        ['id' => '20', 'name' => 'Películas abiertas'],
    ],
    // One series whose episodes are the open movies above.
    'series' => [
        ['id' => 201, 'category' => '20', 'name' => 'Cortos Blender', 'year' => '2006', 'rating' => '7.0', 'cover' => 'img/series.png', 'backdrop' => 'img/series_backdrop.jpg',
         'plot' => 'Cortometrajes producidos de forma abierta por la comunidad de Blender. (CC) Blender Foundation.',
         'episodes' => [1 => [103, 101, 102, 104]]],   // season => vod ids, in order
    ],
];
