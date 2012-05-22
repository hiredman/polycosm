(ns polycosm.primus
  (:require [wall.hack :as wh]
            [cemerick.pomegranate.aether :as pom])
  (:import (org.jboss.modules ModuleIdentifier
                              ModuleLoader
                              ModuleSpec
                              DependencySpec
                              ModuleClassLoaderFactory
                              JarFileResourceLoader
                              ResourceLoaderSpec)
           (java.util.jar Attributes$Name)))

(defn jar-resource-loader [jar-file]
  (first (for [c (.getDeclaredConstructors JarFileResourceLoader)
               :when (= 2 (count (.getParameterTypes c)))]
           (.newInstance
            (doto c (.setAccessible true))
            (into-array Object ["" jar-file])))))

(def default-system-paths
  #{"org/xml/sax"
    "org/xml/sax/helpers"
    "javax/xml/parsers"
    "javax/transaction/xa"
    "javax/management"
    "javax/management/remote"
    "javax/management/openmbean"})

(defn maven-module-loader
  "system paths are similar to :provided scoping in maven, but for the
  module system"
  [& {:keys [system-paths]}]
  (proxy [ModuleLoader] []
    (findModule [module-id]
      (let [mod-cord [(symbol (.getName module-id))
                      (.getSlot module-id)]
            dep-graph (pom/resolve-dependencies :coordinates
                                                [mod-cord])
            mod-spec-b (ModuleSpec/build module-id)
            jar-file (java.util.jar.JarFile.
                      (:file (meta (key (find dep-graph mod-cord)))))]
        (.addDependency mod-spec-b
                        (DependencySpec/createSystemDependencySpec
                         (set (or system-paths default-system-paths))))
        (doseq [[dep-name slot] (get dep-graph mod-cord)
                :let [id (ModuleIdentifier/create
                          (if (namespace dep-name)
                            (str (namespace dep-name)
                                 "/"
                                 (name dep-name))
                            (name dep-name)) slot)]]
          (.addDependency
           mod-spec-b
           (DependencySpec/createModuleDependencySpec
            id)))
        (.addResourceRoot
         mod-spec-b
         (ResourceLoaderSpec/createResourceLoaderSpec
          (jar-resource-loader jar-file)))
        (when-let [manifest (.getManifest jar-file)]
          (when-let [m-attr (.getMainAttributes manifest)]
            (when-let [m-class (.getValue  m-attr
                                           Attributes$Name/MAIN_CLASS)]
              (.setMainClass mod-spec-b m-class))))
        (.addDependency mod-spec-b
                        (DependencySpec/createLocalDependencySpec))
        (.create mod-spec-b)))
    (toString []
      (str "Maven Module Loader@" (.hashCode this)))))

(defn load-module
  "load a module using a loader. module is specified lein style"
  [loader [id version]]
  (.loadModule loader
               (ModuleIdentifier/create
                (if (namespace id)
                  (str (namespace id)
                       "/"
                       (name id))
                  (name id))
                version)))

(defn run
  "runs the main class for a module, same thing as what would be run
  from java -jar the-jar-file. args must be string"
  [module & args]
  (let [old-cl (.getContextClassLoader (Thread/currentThread))]
        (try
          (.setContextClassLoader (Thread/currentThread)
                                  (.getClassLoader module))
          (.run module (into-array String args))
          (finally
            (.setContextClassLoader (Thread/currentThread) old-cl)))))

(defn evil [cl form]
  (read-string
   (let [form-str (pr-str form)
         old-cl (.getContextClassLoader (Thread/currentThread))]
       (try
         (.setContextClassLoader (Thread/currentThread) cl)
         (let [rt (.loadClass cl "clojure.lang.RT")
               compiler (.loadClass cl "clojure.lang.Compiler")
               var- (fn [s]
                      (wh/method
                       rt :var [String String] nil (namespace s) (name s)))
               class (fn [x] (.loadClass cl (name x)))
               deref (fn [x] (wh/method (.getClass x) :deref [] x))
               invoke (fn [x &  args] (wh/method (.getClass x) :invoke []))
               read-string (fn [s]
                             (wh/method rt :readString [String] nil s))
               eval (fn [f]
                      (wh/method compiler :eval [Object] nil f))]
           (eval (read-string (format "(pr-str %s)" form-str))))
         (finally
           (.setContextClassLoader (Thread/currentThread) old-cl))))))
