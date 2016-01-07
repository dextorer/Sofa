Sofa
====
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Sofa-green.svg?style=flat)](https://android-arsenal.com/details/1/2244) [![MethodsCount](https://img.shields.io/badge/Methods count-core: 817 | deps: 15173-e91e63.svg)](http://www.methodscount.com/?lib=com.github.dextorer%3Asofa%3A1.0.0)

A library for Android TV that extends the [Leanback library](https://developer.android.com/tools/support-library/features.html#v17-leanback) capabilities by offering a set of more powerful features.

Features
========

The Leanback library is astonishing in many different aspects, and Google has done an insanely good job at easing the developer's task of creating an awesome TV app. 
But Leanback comes with some limitations, and at some point, those limitations are just too big to be ignored.

These are some of the features that you get by using Sofa:

* _Support for multiple rows per each category_
* _Support for custom fragments and manual focus handling_
* _Backwards compatible to standard implementation_
* _.. the same goes for BrowseSupportFragment!_

Check the video below to see Sofa in action:

[![Sofa - Demo](http://img.youtube.com/vi/fM_2p1sWOD4/0.jpg)](https://www.youtube.com/watch?v=fM_2p1sWOD4 "Sofa - Demo")

How to use
==========

Simply migrate from `android.support.v17.app.BrowseFragment` to `com.sgottard.sofa.BrowseFragment`. **That's it**!

The best place to start is the demo project, more specifically the [`DemoActivity`](https://github.com/dextorer/Sofa/blob/master/demo/src/main/java/com/sgottard/sofademo/DemoActivity.java) class.

---

To showcase a small example of one of the core features offered by Sofa, this is all it takes to **load multiple rows for each header**, something that has been asked for countless times over the past year:

1. Create a `RowsFragment`
3. Create an `ArrayObjectAdapter` with a `ListRowPresenter` and fill it as you normally do
4. Set the adapter to `RowsFragment`
5. Create another `ArrayObjectAdapter` (no need for a `Presenter`)
6. Add a new `ListRow` that contains the `RowsFragment` instance and the corresponding `HeaderItem`
7. Set the adapter to `BrowseFragment`

Pretty simple, uh? All the classes should be familiar to anyone that has used Leanback before, and so should be the use of those components.

---

I started populating [the project's Wiki](https://github.com/dextorer/Sofa/wiki), in order to better explain what differences stand between the `BrowseFragment` component offered by Sofa and its original counterpart, and how to make the best out of Sofa.

Download
========

Download the latest JAR

[![Download](https://api.bintray.com/packages/dextor/maven/sofa/images/download.svg) ](https://bintray.com/dextor/maven/sofa/_latestVersion)

or grab via Gradle

```
dependencies {
    compile 'com.github.dextorer:sofa:1.0.0'
}
```

Dependencies
============

Sofa depends on Leanback to work! Be sure to add the latest Leanback library version to your Gradle build file:

```
compile 'com.android.support:leanback-v17:22.2.0'
```

Caveats
=======

Sofa is built using part of Leanback's source code, mainly the `BrowseFragment` and `BrowseSupportFragment` classes. The direct dependencies for those classes had to be imported as well, but are mostly untouched. Every other class is still part of Leanback.

Contribute
==========

Feedback and contributions are extremely welcome. If you feel like something is missing, please submit a request or simply consider adding it yourself.

Follow me
=========

<a href="https://twitter.com/@rotxed">
  <img src="./doc/images/twitter.png" alt="Twitter" witdh="64" height="64" hspace="10">
</a>
<a href="https://plus.google.com/+SebastianoGottardo">
  <img src="./doc/images/google-plus.png" alt="Google+" witdh="64" height="64" hspace="10">
</a>
<a href="https://www.linkedin.com/profile/view?id=184236138">
  <img src="./doc/images/linkedin.png" alt="LinkedIn" witdh="64" height="64" hspace="10">
</a>


License
=======

```
Copyright 2015, Sebastiano Gottardo.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
