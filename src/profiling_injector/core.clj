(ns profiling-injector.core
  (:gen-class)
  (:import (javassist ClassPool CtClass CtMethod LoaderClassPath)
	   (java.io File)))

(def classPool (ClassPool. true))
(defn get-class
  "Returns a CtClass object for the fully qualified class name"
  [^:String class-name]
  (.get classPool class-name))

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

(defn add-time-profile [ctMethod]
  (let [local-var (gensym)
        class-name (.getName (.getDeclaringClass ctMethod))
        method-name (.getName ctMethod)]
    (doto ctMethod
      (insert-start-time local-var)
      (.insertAfter
       (to-java-code [class-name method-name local-var]
		     com.xora.device.util.StandardLogProvider.profile([0] + "/" + [1] + "/" + (System.currentTimeMillis() - [2])))))))

(defn construct-path [dir-file file-name]
  (str (.getPath dir-file) "/" file-name))

(defn file->class-name [base-dir file]
  (let [base-path (-> base-dir (.replace  File/separatorChar \.) (.replace "//" ".") (.replace \/ \.))
	class-name (-> file (.getPath) (.replace File/separatorChar \.) (.replace (str base-path \.) "") (.replace ".class" ""))]
    class-name))

(defn find-classes [base-dir]
  (filter #(.contains (.getName %) ".class")
	  (tree-seq #(.isDirectory %) #(map
					(fn [file-name] (File. (construct-path % file-name)))
					(.list %))
		    (File. base-dir))))

(defn -main
  "Appends a directory to the default search path and adds logging
  calls to the specified classes in the path. Make sure the ant task
  is forked and that the class path contains everything you will need
  to load"
  [class-base-dir & exclude-classes]
  (println "Searching " class-base-dir " for classes")
  (doseq [class-name (filter (comp not (set exclude-classes)) (map (partial file->class-name class-base-dir) (find-classes class-base-dir)))]
    (when-let [ct-class (get-class class-name)]
      (when-not (.isInterface ct-class)
	(doseq [method (get-methods ct-class)]
	  (try
	    (when-not (.isEmpty method)
	      (add-time-profile method))
	    (catch Exception e
	      (.printStackTrace e)
	      (printf "Class = %s\nmethod = %s" ct-class method)
	      (System/exit 2)))))
      (.writeFile ct-class class-base-dir)))
  (println "Finished editing classes"))