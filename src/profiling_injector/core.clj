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
(defmethod quote-arg ::collection [values arg]
  (map #(quote-arg values %) arg))
(defmethod quote-arg clojure.lang.PersistentVector [values arg]
  (quote-arg values (values (first arg))))

;;; This is sooooo close. I just need to evaluate args.
(defmacro to-java-code
  ([args & body]
     (str
      (apply
       str
       (interpose " " (map #(quote-arg args %) body)))
      \;)))


(defn insert-start-time [ctMethod local-var]
  (doto ctMethod
    (.addLocalVariable (str local-var) CtClass/longType)
    (.insertBefore (str local-var "=System.currentTimeMillis();" ))))

(defn escape-output [args]
  (apply str  (interpose "+" (map quote-arg args))))

(defn append-output [ctMethod & args]
  (doto ctMethod
    (.insertAfter
     (str "System.out.println("
          (escape-output ["[" (.getName (.getDeclaringClass ctMethod)) "]"
                          "[" (.getName ctMethod) "]"])
          " "
          (escape-output args)
          ");"))))

(defn add-time-profile [ctMethod]
  (let [local-var (gensym)]
    (insert-start-time ctMethod local-var)
    (append-output ctMethod "start time = " local-var)
    (append-output ctMethod "end time = " '(System.currentTimeMillis()))
    (.insertAfter ctMethod (str "System.out.println(\"Total time = \" +"
                                "(System.currentTimeMillis() - " local-var
                                "));"))))

(defn -main [& args]
  (doseq [class-name args]
    (let [ct-class (get-class class-name)]
      (doseq [method (get-methods ct-class)]
        (println "modifying method " (.getName method))
        (add-time-profile method))
      (.writeFile ct-class))))