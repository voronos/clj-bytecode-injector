(ns profiling-injector.core
  (:gen-class)
  (:import (javassist ClassPool CtClass CtMethod)))

(defn get-class
  "Returns a CtClass object for the fully qualified class name"
  [^:String class-name]
  (.. (ClassPool/getDefault) (get class-name)))

(defn get-methods [ctClass]
  (.getDeclaredMethods ctClass))

(defn find-methods-like [name-regex ctClass]
  (filter #(re-find name-regex (.getName %)) (get-methods ctClass)))

(defmulti quote-arg (fn [values arg] (class arg)))
(defmethod quote-arg :default [values arg]
  arg)
(defmethod quote-arg String [values arg]
  (str \" arg \"))
(defmethod quote-arg clojure.lang.PersistentList$EmptyList
  [values arg] "()")
(defmethod quote-arg clojure.lang.PersistentVector [values arg]
  (quote-arg values (values (first arg))))
(defmethod quote-arg clojure.lang.PersistentList [values arg]
  (str "(" (apply str (map #(quote-arg values %) arg)) ")"))


(defmacro to-java-code
  ([args & body]
     `(str
      (apply
       str
       (interpose " " (map #(quote-arg ~args %) '(~@body))))
      \;)))

(defn insert-start-time [ctMethod local-var]
  (doto ctMethod
    (.addLocalVariable (str local-var) CtClass/longType)
    (.insertBefore (to-java-code
                    [local-var] [0] = System.currentTimeMillis ()))))

(defn escape-output [args]
  (apply str  (interpose "+" (map quote-arg args))))

;; (defn append-output [ctMethod & args]
;;   (.insertAfter ctMethod (to-java-code [(.getName (.getDeclaringClass ctMethod))
;;                                         (.getName ctMethod)]
;;                                        System.out.print ("["+ [0]+ "][" + [1] + "]")))
;;   (.insertAfter
;;    ctMethod
;;    (to-java-code []
;;                  System.out.println (args))
;;    ;; (str "System.out.println("
;;    ;;      (escape-output ["[" (.getName (.getDeclaringClass ctMethod)) "]"
;;    ;;                      "[" (.getName ctMethod) "]"])
;;    ;;      " "
;;    ;;      (escape-output args)
;;    ;;      ");")
;;    ))

(defn add-time-profile [ctMethod]
  (let [local-var (gensym)]
    ;(insert-start-time ctMethod local-var)
    (doto ctMethod
      (insert-start-time local-var)
      (.insertAfter (to-java-code [local-var] System.out.println("start time = " + [0])))
      (.insertAfter (to-java-code ['(System.currentTimeMillis())] System.out.println("end time = " + [0])))
      (.insertAfter
       (to-java-code ['(System.currentTimeMillis()) local-var]
                     System.out.println("Total time = " + ([0] - [1])))))))

(defn -main [& args]
  (doseq [class-name args]
    (let [ct-class (get-class class-name)]
      (doseq [method (get-methods ct-class)]
        (println "modifying method " (.getName method))
        (add-time-profile method))
      (.writeFile ct-class))))