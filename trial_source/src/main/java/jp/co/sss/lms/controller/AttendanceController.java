package jp.co.sss.lms.controller;

import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.service.StudentAttendanceService;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;

/**
 * 勤怠管理コントローラ
 * 
 * @author 東京ITスクール
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

	@Autowired
	private StudentAttendanceService studentAttendanceService;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageSource messageSource;

	/**
	 * 勤怠管理画面 初期表示
	 * 
	 * @param lmsUserId
	 * @param courseId
	 * @param model
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/detail", method = RequestMethod.GET)
	public String index(HttpSession session, Model model) {

		// 勤怠一覧の取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);
		
		Boolean hasShown = (Boolean) session.getAttribute("hasUnfilledShown");
		if(hasShown == null|| !hasShown) {
		boolean hasUnfilled = studentAttendanceService.hasUnfilledPastAttendance(
				loginUserDto.getLmsUserId(),
				loginUserDto.getCourseId()
				);
		model.addAttribute("hasUnfilled", hasUnfilled);
		session.setAttribute("hasUnfilledShown", true);
		}else {
			model.addAttribute("hasUnfilled", false);
		}

		return "attendance/detail";
	}
	
	/**
	 * 勤怠管理画面 『出勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchIn", method = RequestMethod.POST)
	public String punchIn(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_ATWORK);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchIn();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『退勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchOut", method = RequestMethod.POST)
	public String punchOut(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_LEAVING);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchOut();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『勤怠情報を直接編集する』リンク押下
	 * 
	 * @param model
	 * @return 勤怠情報直接変更画面
	 */
	@RequestMapping(path = "/update")
	public String update(Model model) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		// 勤怠フォームの生成
		AttendanceForm attendanceForm = studentAttendanceService
				.setAttendanceForm(attendanceManagementDtoList);
		model.addAttribute("attendanceForm", attendanceForm);

		return "attendance/update";
	}


	/**
	 * 勤怠情報直接変更画面 『更新』ボタン押下
	 * 
	 * @param attendanceForm
	 * @param model
	 * @param result
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/update", params = "complete", method = RequestMethod.POST)
	public String complete(@Valid AttendanceForm attendanceForm, BindingResult result, Model model)
			throws ParseException {
		
		// 個別フォームのチェック
	    for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
	        DailyAttendanceForm form = attendanceForm.getAttendanceList().get(i);
	        List<ObjectError> errors = form.validate(i, messageSource);
	        errors.forEach(result::addError);
	    }

	    // 入力チェックに引っかかった場合
	    if (result.hasErrors()) {
	    	String errors = result.getAllErrors().stream()
	                .map(e -> "* " + e.getDefaultMessage())
	                .collect(Collectors.joining("<br/>"));
	    
	    	model.addAttribute("error", errors);
	        // プルダウン再設定
	        attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
	        attendanceForm.setHours(attendanceUtil.setHours());
	        attendanceForm.setMinutes(attendanceUtil.setMinutes());

	        model.addAttribute("attendanceForm", attendanceForm);
	        return "attendance/update"; // 勤怠情報直接変更画面に戻す
	    }

	    // 更新処理
	    String message = studentAttendanceService.update(attendanceForm);
	    model.addAttribute("message", message);

	    // 一覧再取得
	    List<AttendanceManagementDto> attendanceManagementDtoList =
	            studentAttendanceService.getAttendanceManagement(
	                    loginUserDto.getCourseId(),
	                    loginUserDto.getLmsUserId());

	    model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);
	    
	    return "attendance/detail";

	} 
}