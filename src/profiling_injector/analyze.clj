(ns profiling-injector.analyze
  (:gen-class))

;; Desired output = Class.method - X calls - Y milliseconds total
;; organized by total time descending

;; Current input = [Class] [method] MILLISECONDS

(defn parse-line [line]
  (when (.startsWith "[" line)
    (map read-string (.split line " "))))

(defn to-hashmap [line-symbols]
  {(keyword (ffirst line-symbols))
   {(keyword (first (fnext line-symbols))) (last line-symbols)}})

;lein jar && java -cp profiling-injector-1.0.0-SNAPSHOT.jar:lib/clojure-1.3.0.jar profiling_injector.analyze < profile.txt
(defn -main [& args]
  ;; Okay, it should be easier than this to iterate over the input. WTF?
  (doseq [input (line-seq (java.io.BufferedReader. System/in))]
    (println input)
    (println (map (comp to-hashmap parse-line) input))))