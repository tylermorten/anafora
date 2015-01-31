# Anafora ... 

A Clojure library designed to make reference requests from local Bloomberg stations.
Uses the Bloomberg java api version 3.7.1

## Usage

You will need to have the Bloomberg java libraries installed into the repo directory
under your current project, i.e. projectdir/repo/bloomberglp/blpapi/3.7.1 or in your local maven
repository.

This in your dependencies:

```
[anaflora "0.1.0-SNAPSHOT"]
```

## Example
```
(ns test
    (:require [anafora.core :refer :all])
    (:import (com.bloomberglp.blpapi SessionOptions)))

(def securities ["31417CTR6 Mtge"])
(def fields ["CPN" "MTG_FACTOR"])

; If you need to change to something other than localhost and default port
; rebind the *sesssion-options* var as in the function below. Otherwise, leave
; out the binding
(defn grab-data []
      (binding [*session-options* (new SessionOptions)]
                (.setServerHost *session-options* "localhost")
                (.setServerPort *session-options* 8194)
                (defreferencerequest my-new-request securities fields)
                (submit-request my-new-request)))
;get the data
[{:cpn "..." :mtg_factor "...."}]
```

## Requires
- Licensed Bloomberg workstation
- Bloomberg Java API

## License

Copyright © 2015 Tyler Morten

Distributed under the Eclipse Public License either version 1.0
