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
(defmacro call-println [& body]
  `(str 'System.out.println \( \) \;))

(defn add-time-profile [ctMethod]
  (let [local-var (gensym)]
    (doto ctMethod
      (.addLocalVariable (str local-var) CtClass/longType)
      (.insertBefore (str local-var "=System.currentTimeMillis();" ))
      (.insertAfter (str "System.out.println(\"local-var =\" + " local-var ");")))))

(defn -main [& args]
  (doseq [class-name args]
    (let [ct-class (get-class class-name)]
      (doseq [method (get-methods ct-class)]
        (println "modifying method " (.getName method))
        (add-time-profile method))
      (.writeFile ct-class))))