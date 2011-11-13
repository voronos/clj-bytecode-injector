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

;;; This might be a macro, because what I want is going to be
;;; (call-println "This value = " x " and the time is " (new Date()))
;;; which should produce "System.out.println(\"This value = \" + x + \"and the time is \" + (new Date()))"
;;; TODO abandoning for now, but soon
;;; This needs to return a string of code. which means replacing any " in body with \" and \ with \\
(defmacro inject-code [& body]
  `(str (clojure.string/escape ~@body {\\ "\\" \" "\""})))
  ;; `(str 'System.out.println \( \) \;))

(defn insert-start-time [ctMethod local-var]
  (doto ctMethod
    (.addLocalVariable (str local-var) CtClass/longType)
    (.insertBefore (str local-var "=System.currentTimeMillis();" ))))

(defmulti quote-arg class)
(defmethod quote-arg :default [arg]
  arg)
(defmethod quote-arg String [arg]
  (str \" arg \"))

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