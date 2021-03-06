define(function(require, exports, module) {
    require('jquery');
    require('jCookie');
	require('../js/common.js');
	require('paginator');
	require('bootstrap');
	require('ueditor_config');
	require('ueditor');

	$(".applyMana").next(".treeview-menu").toggle("slow");
	$(".applyMana").addClass("leftActive");
	$(".epnotice").css("color", "white");
    $(".notice").addClass("active");
    
	var template = require('artTemplate');
	var pubMeth = require('../js/public.js');
    
	var program = {
		page : "1",
		count:'',
		title:'',
		describle:'',
		addtime:'',
		data:'',
		html:'',
		epnoId:'',
		epnoIds:[],
		/**
		 * 添加公告
		 */
		addNotice:function(){
			$.ajax({
				type : "get",
				content : "application/x-www-form-urlencoded;charset=UTF-8",
				url : "../epNotice/insertEpNotice",
				dataType : 'json',
				async : false,
				data : {
					content : program.describle,
					title : program.title,
				},
				success : function(result) {
					console.log(result);	
					if(result.status == 1){
						pubMeth.alertInfo("alert-success","添加成功！");
					}else{
						pubMeth.alertInfo("alert-danger","添加失败！");
					}
				},
				error:function(){
					  pubMeth.alertInfo("alert-danger","请求错误");
				}
			});
		},
		/**
		 * 查询某个公告
		 */
		selectId:function(){
			$.ajax({
				type : "get",
				content : "application/x-www-form-urlencoded;charset=UTF-8",
				url : "../epNotice/selecByCondition",
				dataType : 'json',
				async : false,
				data : {
					epnoId :program.epnoId,			
				},
				success : function(result) {
					console.log(result);	
					if(result.status == 1){
						$(".nottitle").val(result.list[0].title);
					    ue.ready(function(){
					    	ue.setContent(result.list[0].content);
					    });
					}else{
						pubMeth.alertInfo("alert-danger","查询错误");
					}					
				},
				error:function(){
					  pubMeth.alertInfo("alert-danger","请求错误");
				}
			});
		},
		/**
		 * 查询公告
		 */
		selectNotice:function(){
			$.ajax({
				type : "get",
				content : "application/x-www-form-urlencoded;charset=UTF-8",
				url : "../epNotice/selecByCondition",
				dataType : 'json',
				async : false,
				data : {
					page:program.page,
					row:pubMeth.rowsnum,				
				},
				success : function(result) {
					console.log(result);	
					if(result.status == 1){
						program.data = result.list;
						program.count = result.total;
						program.showNotice();
						$("#listInfo").empty();
						$("#listInfo").append(program.html);
						$("#listInfo tr").each(function(i) {
	     					$("td:last a.title",this).click(function(){
	     						$('#addnotice').modal({
	     					 		 backdrop : 'static'
	     						});
	     						program.epnoId = $(this).attr("value");
								program.selectId();
	     				
	     					});
	     				});
					}else{
						pubMeth.alertInfo("alert-danger","查询错误");
					}					
				},
				error:function(){
					  pubMeth.alertInfo("alert-danger","请求错误");
				}
			});
		},
		/**
		 * 展示公告
		 */
		showNotice:function(){
			var order = 1;
			program.html = "";
			var length = program.data.length;
			program.html="";
			for(var i = 0;i < length;i++){
				program.html+=
					'<tr><td><input type="checkbox" value="'+program.data[i].epnoId+'" name="title"/></td>'
				+ '<td>'+order+'</td>'	
			    +      '<td class="show">' +program.data[i].title + '</td>'
			    +      '<td>'+program.data[i].addTime+'</td>'
				+'<td><a href="javascript:;"class="title" value="'+program.data[i].epnoId+'" >修改</a></td>'
			    +  '</tr>';
				order++;
			}
		},
		/**
		 * 删除公告
		 */
		deleteNotice:function(){
			$('.delnotice').on('click', function (e) {
				var valArr = new Array;
				$(":checkbox[name='title']:checked").each(function(i) {
								valArr[i] = $(this).val();
				});
				var vals = valArr.join(',');// 转换为逗号隔开的字符串
				if(vals!=""){
					$("#modalexamdelete").modal(function(){
						backdrop : 'static'
					});
					$(".examquess").html(vals);
					$(".examdelete").click(function(){
						$.ajax({
	     					type : "post",
	     					content : "application/x-www-form-urlencoded;charset=UTF-8",
	     					url:"../epNotice/deleteEpNoticeByIds",		
	     					dataType : 'json',
	     					async : false,
	     					data:{
	     						ids:vals,
	     					},
	     					success:function(result){ 		     						
	     						if(result.status ==1){
	     							pubMeth.alertInfo("alert-success","删除成功！");		     							
	     							program.selectNotice();
	     							$("#modalexamdelete").modal('hide');
	     						}else{
	     							pubMeth.alertInfo("alert-danger","删除失败！");
	     						}
	     					}
	     				});
					});
				}else{
					pubMeth.alertInfo("alert-info","请先勾选删除项！");
				}
		}); 
		},
		/**
		 * 更新公告
		 */
		updateNotice:function(){
			$.ajax({
				type : "get",
				content : "application/x-www-form-urlencoded;charset=UTF-8",
				url : "../epNotice/updateEpNotice",
				dataType : 'json',
				async : false,
				data : {
					epnoId:program.epnoId,
					content:program.describle,
					title:program.title,
				},
				success : function(result) {
					console.log(result);	
					if(result.status == 1){
						pubMeth.alertInfo("alert-success","修改成功！");
					}
				},
				error:function(){
					  pubMeth.alertInfo("alert-danger","请求错误");
				}
			});
		},
	};
	var  ue = UE.getEditor('notdes', {
		   initialFrameWidth:500 ,//初始化编辑器宽度,默认1000
	       initialFrameHeight:200  //初始化编辑器高度,默认320
		});
	pubMeth.getRowsnum("rowsnum");
	var parm = pubMeth.getQueryObject();
	program.examId = parm["id"];
	program.selectNotice();
	program.deleteNotice();
	
	$(".addnotice").click(function(){	
		$('#addnotice').modal({
	 		 backdrop : 'static'
		});				
		$(".nottitle").val("");
		ue.ready(function(){
			ue.setContent("");
		});
		program.epnoId = "";
	});
	$(".savenot").click(function(){
		program.title = $(".nottitle").val();
		ue.ready(function(){
			program.describle = ue.getContent();
		});	
		if(program.epnoId != ""){
			program.updateNotice();
		}else{
			if(program.title != null && program.describle != null){
				program.addNotice();
			}
		}
		$('#addnotice').modal("hide");
		program.selectNotice();
	});

	if(program.count > 0){
		$(".countnum").html(program.count);
	 	$.jqPaginator('#pagination', {	 		
	    	totalCounts : program.count,
	        visiblePages: 5,
	        currentPage: 1,
	        pageSize: parseInt(pubMeth.rowsnum),
	        first: '<li class="first"><a href="javascript:;">首页</a></li>',
	        last: '<li class="last"><a href="javascript:;">尾页</a></li>',
	        page: '<li class="page"><a href="javascript:;">{{page}}</a></li>',
	        onPageChange: function (num, type) {
	      	if(type == 'init') {return;}
	      		program.page = num;
				program.selectNotice();
	        }
        });
	} else {
		$(".pagenum").css("display","none");
	}
});