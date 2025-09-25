package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.ObjectError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 未入力勤怠チェック
	 * 
	 * @param lmsUserId
	 * @param deleteFlg
	 * @param trainingDate
	 * @return 勤怠情報（受講生）テーブルマッパー
	 */
	public boolean hasUnfilledPastAttendance(int lmsUserId, short deleteFlg, Date trainingDate) {
		return tStudentAttendanceMapper.notEnterCount(lmsUserId, deleteFlg, trainingDate) > 0;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定 
	 *
	 * @param attendanceManagementDtoList 
	 * @return 勤怠編集フォーム 
	 */
	public AttendanceForm setAttendanceForm(List<AttendanceManagementDto> attendanceManagementDtoList) {
		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}
		attendanceForm.setHours(attendanceUtil.setHourMap());
		attendanceForm.setMinutes(attendanceUtil.setMinuteMap());

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え 
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(
						String.valueOf(attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}

			// 出勤時刻を分割してセット
			String start = attendanceManagementDto.getTrainingStartTime();
			//例　09:30
			if (start != null && start.length() >= 5 && start.charAt(2) == ':') {
				dailyAttendanceForm.setTrainingStartHour(Integer.valueOf(start.substring(0, 2)));
				dailyAttendanceForm.setTrainingStartMinute(Integer.valueOf(start.substring(3, 5)));
			} else {
				dailyAttendanceForm.setTrainingStartHour(null);
				dailyAttendanceForm.setTrainingStartMinute(null);
			}
			// 退勤時間を分割してセット 
			String end = attendanceManagementDto.getTrainingEndTime();
			if (end != null && end.length() >= 5 && end.charAt(2) == ':') {
				dailyAttendanceForm.setTrainingEndHour(Integer.valueOf(end.substring(0, 2)));
				dailyAttendanceForm.setTrainingEndMinute(Integer.valueOf(end.substring(3, 5)));
			} else {
				dailyAttendanceForm.setTrainingEndHour(null);
				dailyAttendanceForm.setTrainingEndMinute(null);
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(
					dateUtil.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}
		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException 
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {
		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId() : attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper.findByLmsUserId(lmsUserId,
				Constants.DB_FLG_FALSE);
		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付 
			tStudentAttendance.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き 
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}

			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());

			// 出勤時刻整形 
			TrainingTime trainingStartTime = null;
			Integer startHour = dailyAttendanceForm.getTrainingStartHour();
			Integer startMinute = dailyAttendanceForm.getTrainingStartMinute();

			// 片方だけ入力チェックは不要 
			if (startHour != null && startMinute != null) {
				trainingStartTime = new TrainingTime(startHour, startMinute);
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingStartTime("");
			}

			//退勤時刻整形 
			TrainingTime trainingEndTime = null;
			Integer endHour = dailyAttendanceForm.getTrainingEndHour();
			Integer endMinute = dailyAttendanceForm.getTrainingEndMinute();
			if (endHour != null && endMinute != null) {
				trainingEndTime = new TrainingTime(endHour, endMinute);
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingEndTime("");
			}

			// 中抜け時間 
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
						trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考 
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 *勤怠フォームの入力チェック 
	 * 
	 * @param attendanceForm
	 * @param messageSource
	 * @return エラーメッセージ
	 */
	public List<ObjectError> validateAttendanceForm(
			AttendanceForm attendanceForm, MessageSource messageSource) {

		//エラーを格納するリスト
		List<ObjectError> errors = new ArrayList<>();

		//勤怠リストの件数分のループ
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm form = attendanceForm.getAttendanceList().get(i);

			// 勤務時間比較用の変数（初期化しておく）
			int start = -1;
			int end = -1;

			// b. 出勤時間の片方のみ入力
			if (isHalfInput(form.getTrainingStartHour(), form.getTrainingStartMinute())) {
				errors.add(new ObjectError(
						"attendanceList[" + i + "].trainingStartHour",
						new String[] { "input.invalid" },
						new Object[] { "出勤時間" },
						messageSource.getMessage("input.invalid", new Object[] { "出勤時間" }, Locale.JAPAN)));
			}

			//c.退勤時間の片方のみ入力
			if (isHalfInput(form.getTrainingEndHour(), form.getTrainingEndMinute())) {
				errors.add(new ObjectError(
						"attendanceList[" + i + "].trainingEndHour",
						new String[] { "input.invalid" },
						new Object[] { "退勤時間" },
						messageSource.getMessage("input.invalid", new Object[] { "退勤時間" }, Locale.JAPAN)));
			}

			//d.出勤なし&退勤だけ入力
			if (isEmpty(form.getTrainingStartHour(), form.getTrainingStartMinute())
					&& !isEmpty(form.getTrainingEndHour(), form.getTrainingEndMinute())) {
				errors.add(new ObjectError(
						"attendanceList[" + i + "].trainingStartHour",
						new String[] { "attendance.punchInEmpty" },
						null,
						messageSource.getMessage("attendance.punchInEmpty", null, Locale.JAPAN)));
			}

			//e.出勤時間＞退勤時間
			if (!isEmpty(form.getTrainingStartHour(), form.getTrainingStartMinute())
					&& !isEmpty(form.getTrainingEndHour(), form.getTrainingEndMinute())) {

				Integer sh = form.getTrainingStartHour();
				Integer sm = form.getTrainingStartMinute();
				Integer eh = form.getTrainingEndHour();
				Integer em = form.getTrainingEndMinute();

				if (sh != null && sm != null && eh != null && em != null) {
					start = sh * 60 + sm;
					end = eh * 60 + em;

					if (start > end) {
						errors.add(new ObjectError(
								"attendanceList[" + i + "].trainingStartHour",
								new String[] { "attendance.trainingTimeRange" },
								new Object[] { i + 1 },
								messageSource.getMessage("attendance.trainingTimeRange", new Object[] { i + 1 },
										Locale.JAPAN)));
					}
				}
			}

			// f. 中抜け時間＞勤務時間（startとendがセットされてるときだけチェック）
			if (start >= 0 && end >= 0 && form.getBlankTime() != null && form.getBlankTime() > (end - start)) {
				errors.add(new ObjectError(
						"attendanceList[" + i + "].blankTime",
						new String[] { "attendance.blankTimeError" },
						null,
						messageSource.getMessage("attendance.blankTimeError", null, Locale.JAPAN)));
			}
		}

		return errors;
	}

	//補助メソッド

	/**
	 * 時刻の「時」と「分」の片方だけが入力されているかどうか
	 */
	private boolean isHalfInput(Integer h, Integer m) {
		return (h != null) ^ (m != null);

	}

	/**
	 * 時刻の「時」と「分」の両方が未入力かどうか
	 */
	private boolean isEmpty(Integer h, Integer m) {
		return (h == null) && (m == null);
	}

}
