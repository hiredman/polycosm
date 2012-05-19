(ns polycosm.test.primus
  (:use [polycosm.primus]
        [clojure.test]))

(deftest t-stuff
  (let [l (maven-module-loader)
        c12 (load-module l '[org.clojure/clojure "1.2.0"])
        c13 (load-module l '[org.clojure/clojure "1.3.0"])
        c14 (load-module l '[org.clojure/clojure "1.4.0"])]
    (are [module map] (= (evil (.getClassLoader module)
                               '*clojure-version*)
                         map)
         c12 {:major 1, :minor 2, :incremental 0, :qualifier ""}
         c13 {:major 1, :minor 3, :incremental 0, :qualifier nil}
         c14 {:major 1, :minor 4, :incremental 0, :qualifier nil})))
