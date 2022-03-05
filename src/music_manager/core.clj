(ns music-manager.core
  (:import java.util.zip.ZipFile
           (java.nio.file Files
                          Paths))
  (:require [clojure.java.io :as io])
  (:gen-class))

(def library "/mnt/data/Music/dan")
(def album-source "/home/dan/Downloads")

(def zip-regex #"(.*) - (.*)\.zip")

(defn list-files [pattern dir]
  (->> (clojure.java.io/file dir)
       file-seq
       (filter #(re-matches pattern (.getName %)))))

(defn list-bandcamp-zips [dir]
  (list-files zip-regex dir))

(def flac-regex #".* - .* - (.*.flac)")

(defn rename-file [old-name]
  (let [[_ new-name] (re-matches flac-regex old-name)]
    (if new-name
      new-name
      old-name)))

(defn rename-flacs [dir]
  (let [flacs (list-files flac-regex dir)]
    (doseq [flac flacs]
      (let [[_ new-name] (re-matches flac-regex (.getName flac))
            dir (.getParent flac)
            new-path (str dir "/" new-name)
            old-file (io/file (.getPath flac))]
        (println "Renaming" (.getPath flac) "->" new-path)
        (io/copy old-file (io/file new-path))
        (io/delete-file old-file)))))

(defn classify-zip-entry [entry]
  (let [name (.getName entry)]
    (if (re-matches #".*\.flac" name)
      (let [[_ filename] (re-matches #".* - (\d+ .*)" name)]
        {:track entry
         :name filename})
      {:resource entry
       :name name})))

(defn parse-zip [zip]
  (let [[_ artist album] (re-matches zip-regex (.getName zip))
        contents (->> (ZipFile. zip)
                      .entries
                      iterator-seq
                      (mapv classify-zip-entry))]
    {:path zip
     :artist artist
     :album {:name album
             :contents contents}}))

(defn classify-track-file [track]
  (let [name (.getName track)]
    (if (re-matches #".*\.(flac|ogg|mp3)" name)
      {:track track
       :name name}
      {:resource track
       :name name})))

(defn load-album [album-file]
  (let [name (.getName album-file)]
    [name
     {:name name
      :contents (->> (.listFiles album-file)
                     (mapv classify-track-file))}]))

(defn load-artist [artist-dir]
  {:path (.getPath artist-dir)
   :artist (.getName artist-dir)
   :albums (into {} (mapv load-album
                          (.listFiles artist-dir)))})

(defn load-library [dir]
  (->> (clojure.java.io/file dir)
       .listFiles
       (mapv load-artist)))

(defn unzip [path zip]
  (let [stream (-> (:path zip)
                   io/input-stream
                   (java.util.zip.ZipInputStream.))]
    (loop [entry (.getNextEntry stream)]
      (when entry
        (let [extract-path (str path "/" (rename-file (.getName entry)))]
          (println "extracting" extract-path)
          (io/copy stream (io/file extract-path))
          (recur (.getNextEntry stream)))))))

(defn install-new-album [zip]
  (let [artist (:artist zip)
        album (get-in zip [:album :name])
        path (str library "/" artist "/" album)]
    (println "Creating" path "if not already present")
    (Files/createDirectories (Paths/get path (make-array String 0))
                             (make-array java.nio.file.attribute.FileAttribute 0))
    (unzip path zip)))

(comment
  (def lib (load-library library))

  (def zips (->> (list-bandcamp-zips album-source)
                 (map parse-zip)))

  (->> zips
       first
       #_install-new-album)

  (do
    (->> (list-bandcamp-zips album-source)
         (map parse-zip)
         (map (fn [zip]
                (if-let [lib-val (get-in lib [(:artist zip) (:album zip)])]
                  (println "Album" (:album zip) "is already present, skipping...")
                  (install-new-album zip)))))
    (println "Done")))


