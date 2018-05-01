package com.jamesniedfeldt.collectionorganizer

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_edititem.*
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/* The activity which shows individual items. Depending on from where it was called, it s
 * serves a different purpose. This purpose is determined by an intent passed into it.
 */

const val PIC_REQUESTED = 123
const val NEW_FROM_MAIN = "NEW_FROM_MAIN"
const val NEW_FROM_FILTERED = "NEW_FROM_FILTERED"
const val EDIT_FROM_FILTERED = "EDIT_FROM_FILTERED"
const val FROM_RANDOMIZER = "FROM_RANDOMIZER"

class EditItemActivity : AppCompatActivity(), SensorEventListener{
    var delete = true
    var imageUri: Uri? = null
    var fileMade = false
    var photo: File? = null
    var toReturn: Intent? = null

    //For randomizer only
    var lastShake: Long = 0
    var itemNum = 0
    var db: CollectionDatabase? = null
    var manager: SensorManager? = null
    var detector: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edititem)

        val info = intent.extras

        //Determine how to setup the activity
        when {
            info.containsKey(NEW_FROM_MAIN) -> setupNewFromMain()
            info.containsKey(NEW_FROM_FILTERED) -> setupNewFromFiltered(info.getString(NEW_FROM_FILTERED))
            info.containsKey(EDIT_FROM_FILTERED) -> setupEditFromFiltered(info)
            info.containsKey(FROM_RANDOMIZER) -> setupFromRandomizer(info.getString(FROM_RANDOMIZER))
        }

        //If sent from the item randomizer, we don't want picture editable
        if(!info.containsKey(FROM_RANDOMIZER)){
            edit_pic.setOnClickListener { view ->
                val getPic = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                if (photo != null) {
                    imageUri = Uri.fromFile(photo)
                    getPic.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                    startActivityForResult(getPic, PIC_REQUESTED)
                }
            }
        }
    }

    //If activity is acting as randomizer, unregister the sensor
    override fun onPause(){
        if(manager != null){
            manager!!.unregisterListener(this)
        }
        super.onPause()
    }

    //If activity is acting as randomizer, register the sensor
    override fun onResume() {
        if(manager != null && detector != null){
            manager!!.registerListener(this, detector, SensorManager.SENSOR_STATUS_ACCURACY_LOW)
        }
        super.onResume()
    }

    //Borrowed from https://developer.android.com/training/camera/photobasics.html
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PIC_REQUESTED && resultCode == RESULT_OK) {
            edit_pic.setImageURI(null)
            edit_pic.setImageURI(imageUri)
        }
    }

    //Borrowed from https://developer.android.com/training/camera/photobasics.html
    //Create the photo file to save to
    private fun createFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "JPEG_${timeStamp}_"
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        val image = File.createTempFile(fileName, ".jpg", directory)
        return image
    }

    //Activity is closed
    override fun finish(){
        //If activity is acting as randomizer, unregister sensor
        if(manager != null){
            manager!!.unregisterListener(this)
        }

        //If activity is killed by anything other than "ok", delete the photo file
        if(delete){
            // Photo file is now dead space, get rid of it
            if(photo != null){
                photo!!.delete()
            }
            super.finish()
        }
        else{
            super.finish()
        }
    }

    //Activity is called via MainActivity (new menu item)
    private fun setupNewFromMain(){
        try {
            photo = createFile()
        } catch (x: IOException) {
            Log.i("ERROR", "Couldn't create file")
        }

        button_ok.setOnClickListener { _ ->
            if(edit_name.text.toString() == ""){
                Toast.makeText(this, "Name cannot be blank.", Toast.LENGTH_LONG).show()
            }
            else if(edit_category.text.toString() == ""){
                Toast.makeText(this, "Category cannot be blank", Toast.LENGTH_LONG).show()
            }
            else {
                toReturn = Intent(this, MainActivity::class.java)
                val itemStrings = arrayListOf(edit_name.text.toString(),
                        edit_category.text.toString(),
                        rating_bar.rating.toInt().toString(),
                        imageUri.toString())

                intent.putExtra("SUCCESS_NEW", itemStrings)
                setResult(RESULT_OK, intent)
                delete = false
                finish()
            }
        }

        button_cancel.setOnClickListener { _ ->
            delete = true
            finish()
        }
    }

    //Activity is called from FilteredCategoryActivity (new menu item)
    private fun setupNewFromFiltered(category: String){
        edit_category.setText(category)
        try {
            photo = createFile()
        } catch (x: IOException) {
            Log.i("ERROR", "Couldn't create file")
        }

        button_ok.setOnClickListener { _ ->
            if(edit_name.text.toString() == ""){
                Toast.makeText(this, "Name cannot be blank.", Toast.LENGTH_LONG).show()
            }
            else if(edit_category.text.toString() == ""){
                Toast.makeText(this, "Category cannot be blank", Toast.LENGTH_LONG).show()
            }
            else {
                toReturn = Intent(this, MainActivity::class.java)
                val itemStrings = arrayListOf(edit_name.text.toString(),
                        edit_category.text.toString(),
                        rating_bar.rating.toInt().toString(),
                        imageUri.toString())

                intent.putExtra("SUCCESS_NEW", itemStrings)
                setResult(RESULT_OK, intent)
                delete = false
                finish()
            }
        }

        button_cancel.setOnClickListener { _ ->
            delete = true
            finish()
        }
    }

    //Activity is called from FilteredCategoryActivity (list item)
    private fun setupEditFromFiltered(info: Bundle){
        val strings = info.getStringArrayList(EDIT_FROM_FILTERED)
        photo = File(Uri.parse(strings[3]).path)
        imageUri = Uri.fromFile(photo)
        fileMade = true
        delete = false

        edit_name.setText(strings[0])
        edit_category.setText(strings[1])
        rating_bar.rating = strings[2].toFloat()
        edit_pic.setImageURI(Uri.parse(strings[3]))

        button_ok.setOnClickListener { _ ->
            if(edit_name.text.toString() == ""){
                Toast.makeText(this, "Name cannot be blank.", Toast.LENGTH_LONG).show()
            }
            else if(edit_category.text.toString() == ""){
                Toast.makeText(this, "Category cannot be blank", Toast.LENGTH_LONG).show()
            }
            else {
                toReturn = Intent(this, MainActivity::class.java)
                val itemStrings = arrayListOf(edit_name.text.toString(),
                        edit_category.text.toString(),
                        rating_bar.rating.toInt().toString(),
                        imageUri.toString(),
                        strings[4]) //Id of the item needs to be returned

                intent.putExtra("SUCCESS_EDIT", itemStrings)
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        button_cancel.setOnClickListener { _ ->
            finish()
        }
    }

    //Activity is called from FilteredCategoryActivity (randomizer)
    private fun setupFromRandomizer(category: String){
        db = CollectionDatabase(this, true)
        db!!.retrieveByCategory(category)
        delete = false
        itemNum = db!!.collectionSize
        manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        detector = manager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        manager!!.registerListener(this, detector, SensorManager.SENSOR_STATUS_ACCURACY_LOW)

        button_ok.visibility = View.INVISIBLE
        button_cancel.visibility = View.INVISIBLE
        button_back.visibility = View.VISIBLE
        edit_name.isEnabled = false
        edit_category.isEnabled = false
        edit_category.setText(category)
        rating_bar.setIsIndicator(true)

        button_back.setOnClickListener { _ ->
            finish()
        }
    }

    //When shaken, display a random item
    private fun displayRandomItem(){
        val index = Random().nextInt(itemNum)
        val item = db!!.retrieveItem(index)

        edit_name.setText(item.name)
        rating_bar.rating = item.rating.toFloat()
        edit_pic.setImageURI(item.pic)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event != null){
            var x = event.values[0]
            var y = event.values[1]
            var z = event.values[2]
            var force = Math.abs(x + y + z)

            if(force > 10){
                //Ignore if there was a shake recently
                if(System.currentTimeMillis() - lastShake < 500){
                    lastShake = System.currentTimeMillis()
                }
                else{
                    lastShake = System.currentTimeMillis()
                    displayRandomItem()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //Ignore
    }
}
