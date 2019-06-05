package com.example.ocr.webscraper;

public interface Img2Bitmap
{
    void onConvertComplete(byte[] imageData);
    String buildScript1= "Number.prototype.toUInt=function(){ return this<0?this+4294967296:this; }; "+
    "Number.prototype.bytes32=function(){ return [(this>>>24)&0xff,(this>>>16)&0xff,(this>>>8)&0xff,this&0xff]; }; "+
    "Number.prototype.bytes16sw=function(){ return [this&0xff,(this>>>8)&0xff]; }; "+

    "Array.prototype.adler32=function(start,len){ "+
    "	switch(arguments.length){ case 0:start=0; case 1:len=this.length-start; } "+
    "	var a=1,b=0; "+
    "	for(var i=0;i<len;i++){ "+
    "		a = (a+this[start+i])%65521; b = (b+a)%65521; "+
    "	} "+
    "	return ((b << 16) | a).toUInt(); "+
    "};"+

    "Array.prototype.crc32=function(start,len){ "+
    "	switch(arguments.length){ case 0:start=0; case 1:len=this.length-start; } "+
    "	var table=arguments.callee.crctable; "+
    "	if(!table){ "+
    "		table=[]; "+
    "		var c; "+
    "		for (var n = 0; n < 256; n++) { "+
    "			c = n; "+
    "			for (var k = 0; k < 8; k++) "+
    "				c = c & 1?0xedb88320 ^ (c >>> 1):c >>> 1; "+
    "			table[n] = c.toUInt(); "+
    "		} "+
    "		arguments.callee.crctable=table; "+
    "	} "+
    "	var c = 0xffffffff; "+
    "	for (var i = 0; i < len; i++) "+
    "		c = table[(c ^ this[start+i]) & 0xff] ^ (c>>>8); "+
    "	return (c^0xffffffff).toUInt(); "+
    "}; ";

    String buildScript2=
    "(function(){ "+
    "	var toDataURL=function(){ "+
    "		var imageData=Array.prototype.slice.call(this.getContext(\"2d\").getImageData(0,0,this.width,this.height).data); "+
    "		var w=this.width; "+
    "		var h=this.height; "+
    "		var stream=[ "+
    "			0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a, "+
    "			0x00,0x00,0x00,0x0d,0x49,0x48,0x44,0x52 "+
    "		]; "+
    "		Array.prototype.push.apply(stream, w.bytes32() ); "+
    "		Array.prototype.push.apply(stream, h.bytes32() ); "+
    "		stream.push(0x08,0x06,0x00,0x00,0x00); "+
    "		Array.prototype.push.apply(stream, stream.crc32(12,17).bytes32() ); "+
    "		var len=h*(w*4+1); "+
    "		for(var y=0;y<h;y++) "+
    "			imageData.splice(y*(w*4+1),0,0); "+
    "		var blocks=Math.ceil(len/32768);"+
    "		Array.prototype.push.apply(stream, (len+5*blocks+6).bytes32() );"+
    "		var crcStart=stream.length; "+
    "		var crcLen=(len+5*blocks+6+4); "+
    "		stream.push(0x49,0x44,0x41,0x54,0x78,0x01); "+
    "		for(var i=0;i<blocks;i++){ "+
    "			var blockLen=Math.min(32768,len-(i*32768)); "+
    "			stream.push(i==(blocks-1)?0x01:0x00); "+
    "			Array.prototype.push.apply(stream, blockLen.bytes16sw() ); "+
    "			Array.prototype.push.apply(stream, (~blockLen).bytes16sw() ); "+
    "			var id=imageData.slice(i*32768,i*32768+blockLen); "+
    "			Array.prototype.push.apply(stream, id ); "+
    "		} "+
    "		Array.prototype.push.apply(stream, imageData.adler32().bytes32() ); "+
    "		Array.prototype.push.apply(stream, stream.crc32(crcStart, crcLen).bytes32() ); "+

    "		stream.push(0x00,0x00,0x00,0x00,0x49,0x45,0x4e,0x44); "+
    "		Array.prototype.push.apply(stream, stream.crc32(stream.length-4, 4).bytes32() ); "+
    "		return \"data:image/png;base64,\"+btoa(stream.map(function(c){ return String.fromCharCode(c); }).join('')); "+
    "	}; "+

    "	var tdu=HTMLCanvasElement.prototype.toDataURL; "+

    "	HTMLCanvasElement.prototype.toDataURL=function(type){ "+

    "			var res=tdu.apply(this,arguments); "+
    "			if(res == \"data:,\"){ "+
    "				HTMLCanvasElement.prototype.toDataURL=toDataURL; "+
    "				return this.toDataURL(); "+
    "			}else{ "+
    "				HTMLCanvasElement.prototype.toDataURL=tdu; "+
    "				return res; "+
    "			} "+
    "	} "+
    "})(); "+
    "var canvas = document.createElement(\"canvas\");"+
    "document.body.appendChild(canvas);"+
    "canvas.width = img.width;"+
    "canvas.height = img.height;"+
    "canvas.style='display:none';"+
    "var ctx = canvas.getContext(\"2d\");"+
    "ctx.drawImage(img,0,0,img.clientWidth,img.clientHeight);";

    String buildScript3= "var dataURL = canvas.toDataURL();"+
    "HtmlViewer.getBase64ImageString(dataURL.toString());";
    String updateScript="var MutationObserver = window.MutationObserver || window.WebKitMutationObserver || window.MozMutationObserver;"+
    "var observer = new MutationObserver(function(mutations){"+
    "mutations.forEach(function(mutationRecord){"+
    "console.log(' changed: ' + mutationRecord.type + ', '+mutationRecord.attributeName);"+
    "if(mutationRecord.type=='childList') {"+
    "console.log(mutationRecord.addedNodes);"+
    "mutationRecord.addedNodes.forEach(function(node){"+
    "if(node.id=='capatcha'){"+
    "/*captcha is updated*/"+
    "}"+
    "else if(node.id=='userMessages'){"+
    "/*error message : do nothin*/"+
    "}"+
    "else if(node.id=='resultPanel'){"+
    "/*table is updated: put td into rows*/"+
    "}"+
    "});"+
    "}"+
    "});"+
    "});"+
    "var page_wrapper = document.getElementById('page-wrapper');"+
    "observer.observe(page_wrapper, {"+
    "childList: true,"+
    "subtree: true"+
    "});";

}
