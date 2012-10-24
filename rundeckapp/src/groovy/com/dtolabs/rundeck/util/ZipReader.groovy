package com.dtolabs.rundeck.util

import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry

/*
 * Copyright 2012 DTO Labs, Inc. (http://dtolabs.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
 
/*
 * ZipReader.java
 * 
 * User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 * Created: 10/11/12 11:58 AM
 * 
 */
/**
 * Simple class for processing ZipInputStream in a builder style. Use closures to define structure of dirs to read,
 * and patterns of files to find.  You can define catchalls for files or dirs at any level.  You can also define
 * that particular dirs or files are required, and if not found then the ZipReader will throw an exception.
 * Example:
 * <pre>
 *     new ZipReader(zinput).read{
 *         dir('toplevel'){
 *             dir('sub1'){
 *                 copyRecursiveTo("localpath/on/disk")
 *             }
 *             dir('sub2'){
 *                 required=true
 *                 each('*.xml'){ name,input->
 *                     copyTo('localdir/'+name)
 *                 }
 *             }
 *             dir('sub3'){
 *                 file("monkey.txt"){name,input->
 *                     required=true
 *                     copyTo('localfile.txt')
 *                 }
 *                 file("monkey2.txt"){name,input->
 *                     //read input
 *                 }
 *             }
 *         }
 *         anydir{
 *             //executed with any dirs not matched
 *         }
 *         anyfile{ name,input->
 *             //executed with any files not matched
 *         }
 *     }
 *     </pre>
 */
class ZipReader {
    private ZipInputStream input
    private paths=[]
    private context=[]
    private curdir=[dirs:[:],files:[:]]
    private filectx=null
    private catchallfile=null
    private debug=false

    ZipReader(ZipInputStream input) {
        this.input = input
    }
    private debug(t){
        if(debug){System.err.println("DEBUG: "+t)}
    }
    public static void copyStream(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[1024 * 4];
        int c;
        while (-1 != (c = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, c);
        }
    }
    static String toDirName(String name) {
        return name.replaceAll('(.+?)/*$', '$1/')
    }
    static String namepart(String name){
        def ndx = name.lastIndexOf('/')
        return ndx >=0?name.substring(ndx+1):name
    }
    static String basedir(String name){
        def ndx = name.lastIndexOf('/')
        return ndx >=0 && ndx<name.length()-1 ? name.substring(0,ndx):name
    }
    private pushCtx(name){
        paths<<name
        context<<curdir
        curdir= name=='*'?curdir.anydir:curdir.dirs[name]//[dirs: [:], files: [:]]
    }
    private popCtx(){
        paths.pop()
        curdir=context.pop()
    }
    def copyToTemp(){
        copyTo(File.createTempFile('zipreader','temp'))
    }
    def copyTo(String destination){
        copyTo(new File(destination))
    }
    def copyTo(File dest){
        //copy current
        if(!filectx){
            throw new IllegalStateException("Cannot copyTo unless in a file context")
        }
        dest.withOutputStream {out->
            copyStream(input,out)
        }
        dest
    }
    def copyToDir(String destdir){
        copyToDir(new File(destdir))
    }
    def copyToDir(File destdir){
        //copy current
        copyTo(new File(destdir, namepart(filectx.name)))
    }
    private readFile(ZipEntry entry, Closure clos){
        clos.delegate=this
        filectx=entry
        def paths = entry.name.split('/') as List
        def fname = paths.pop()
        def path = paths.join('/') + '/'
        if(clos.maximumNumberOfParameters==3){
            clos.call(path,fname,input)
        }else if(clos.maximumNumberOfParameters==2){
            clos.call(entry.name,input)
        }else if(clos.maximumNumberOfParameters==1){
            clos.call(entry.name)
        }else{
            clos.delegate=[zip:this,name:entry.name,input:input]
            clos.call()
        }
        filectx = null
    }
    def read(Closure clos){
        debug("read closure: ${clos.class}")
        clos.delegate=this
        if (clos.maximumNumberOfParameters == 1 && paths) {
            clos.call(paths.last())
        }else{
            clos.call()
        }
        //now process zip input based on registered handlers

        debug("begin process next entry")
        def ZipEntry entry = input.getNextEntry()
        while(entry!=null){
//            debug("entry: ${entry.name}, isdir: ${entry.isDirectory()}")
            if (entry.isDirectory()) {
//                debug("dir: ${entry.name} (${curdir.dirs.keySet()})")
            }else{
                //file
                def fname=entry.name
                def parts = fname.split('/') as List
                def fpart = parts.pop()
                def dpart = parts.join('/') + '/'

                //visit/traverse curdir based on dir length and file
                def founddir=curdir
                parts.each{d->
                    if(founddir?.dirs[d+'/']){
                        founddir= founddir.dirs[d + '/']
                    }else if(founddir?.anydir){
                        founddir=founddir.anydir
                    }else{
                        //fallback to catchall
                        founddir=null
                    }
                }
                debug("founddir for file: $founddir")
                if(founddir){
                    def found = founddir.files?.find {k, v -> fpart==k}?: founddir.files?.find {k, v -> fpart.matches(k)}
                    if (found) {
                        debug("match file: ${found}")
                        readFile(entry, found.value)
                    } else if (founddir['anyfile']) {
                        debug("match any file")
                        readFile(entry, founddir['anyfile'])
                    }else{
                        debug("unmatched file: $fname")
                    }
                }else if(catchallfile){
                    debug("catchall file")
                    readFile(entry, catchallfile)
                }else{
                    //not matched file
                    debug("File not not matched: $fname")
                }
            }
            input.closeEntry()
            entry = input.getNextEntry()
        }
    }

    def dir(String name, Closure clos){
        if(name=='*/'){
            return anydir(clos)
        }
        def dirname = toDirName(name)
        debug("add dir closure: $dirname")
        curdir.dirs[dirname] = [dirs: [:], files: [:]]
        pushCtx(dirname)
        clos.delegate = this
        if (clos.maximumNumberOfParameters == 1 && paths) {
            clos.call(paths.last())
        } else {
            clos.call()
        }
        popCtx()
    }
    def anydir(Closure clos){
        debug("add anydir closure")
        curdir.anydir = [dirs: [:], files: [:]]
        pushCtx('*')
        clos.delegate = this
        clos.call()
        popCtx()
    }
    def anyfile(Closure clos){
        debug("add anyfile closure")
        curdir['anyfile'] = clos
    }
    def file(String name, Closure clos){
        debug("add file closure: $name")
        if(name=='*'||name=='*.*'){
            return anyfile(clos)
        }
        curdir.files[name]=clos
    }
    def catchall(Closure clos){
        catchallfile=clos
    }

    def methodMissing(String name, args) {
        if (name.endsWith('/') && args.length == 1 && args[0] instanceof Closure) {
            return this.dir(name, args[0])
        } else if (name.contains('.') && args.length == 1 && (args[0] instanceof Closure || args[0] instanceof String || args[0] instanceof File || args[0] instanceof InputStream)) {
            return this.file(name, args[0])
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }
}
