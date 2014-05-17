TravelBar
=========
TravelBar floats a progress bar over other running apps, which increases as you near your
destination.  I came up with the idea after missing my stop on the bus in the mornings while on the
way to work.

![](https://i.imgur.com/7X2YBEk.png)

Building
--------
You need to populate `travelbar/src/main/AndroidManifest.xml` with a
[Google Maps API key](http://www.vogella.com/tutorials/AndroidGoogleMaps/article.html#maps_key4),
and Maps browser key.

Then import to Android Studio and build.

You can also build from the command line using:

```bash
gradle build
```

## License

    Copyright 2014 Paul Bourke

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
