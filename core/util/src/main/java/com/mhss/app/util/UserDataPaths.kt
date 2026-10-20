package com.mhss.app.util

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

object UserDataPaths {
    fun userRoot(firestore: FirebaseFirestore, uid: String): DocumentReference =
        firestore.collection("users").document(uid)

    fun notesCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("notes")

    fun foldersCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("folders")

    fun tasksCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("tasks")

    fun diaryCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("diary")

    fun bookmarksCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("bookmarks")

    fun metaCollection(firestore: FirebaseFirestore, uid: String): CollectionReference =
        userRoot(firestore, uid).collection("meta")
}
