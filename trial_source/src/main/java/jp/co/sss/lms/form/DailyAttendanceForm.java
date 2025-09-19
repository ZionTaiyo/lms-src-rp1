package jp.co.sss.lms.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.validation.ObjectError;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	/** 出勤時間（時間単位）*/
	private String trainingStartHour;
	/** 出勤時間（分単位）*/
	private String trainingStartMinute;
	/** 退勤時間 */
	private String trainingEndTime;
	/** 退勤時間（時間単位）*/
	private String trainingEndHour;
	/** 退勤時間（分単位）*/
	private String trainingEndMinute;
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	@Size(max = 100, message = "{maxlength}")
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;
	
	//入力チェック用メソッド
	// DailyAttendanceForm.java
	public List<ObjectError> validate(int index, MessageSource messageSource) {
	    List<ObjectError> errors = new ArrayList<>();

	    // a. 備考文字数は @Size でチェック済み

	    // b. 出勤時間 片方のみ
	    if (isHalfInput(trainingStartHour, trainingStartMinute)) {
	        errors.add(new ObjectError(
	                "attendanceList[" + index + "].trainingStartHour",
	                new String[]{"input.invalid"},
	                new Object[]{"出勤時間"},
	                messageSource.getMessage("input.invalid", new Object[]{"出勤時間"}, Locale.JAPAN)
	        ));
	    }

	    // c. 退勤時間 片方のみ
	    if (isHalfInput(trainingEndHour, trainingEndMinute)) {
	        errors.add(new ObjectError(
	                "attendanceList[" + index + "].trainingEndHour",
	                new String[]{"input.invalid"},
	                new Object[]{"退勤時間"},
	                messageSource.getMessage("input.invalid", new Object[]{"退勤時間"}, Locale.JAPAN)
	        ));
	    }

	    // d. 出勤なし & 退勤あり
	    if (isEmpty(trainingStartHour, trainingStartMinute) && !isEmpty(trainingEndHour, trainingEndMinute)) {
	        errors.add(new ObjectError(
	                "attendanceList[" + index + "].trainingStartHour",
	                new String[]{"attendance.punchInEmpty"},
	                null,
	                messageSource.getMessage("attendance.punchInEmpty", null, Locale.JAPAN)
	        ));
	    }

	    // e. 出勤 > 退勤（両方入力されている場合のみチェック）
	    if (!isEmpty(trainingStartHour, trainingStartMinute) && !isEmpty(trainingEndHour, trainingEndMinute)) {
	        try {
	            int start = Integer.parseInt(trainingStartHour) * 60 + Integer.parseInt(trainingStartMinute);
	            int end = Integer.parseInt(trainingEndHour) * 60 + Integer.parseInt(trainingEndMinute);
	            if (start > end) {
	                errors.add(new ObjectError(
	                        "attendanceList[" + index + "].trainingStartHour",
	                        new String[]{"attendance.trainingTimeRange"},
	                        new Object[]{index + 1},
	                        messageSource.getMessage("attendance.trainingTimeRange", new Object[]{index + 1}, Locale.JAPAN)
	                ));
	            }
	        } catch (NumberFormatException e) {
	            // ここには来ないはず、片方だけ入力のチェックですでにエラー追加済み
	        }
	    }

	    // f. 中抜け時間 > 勤務時間（両方入力されている場合のみ）
	    if (blankTime != null && blankTime > 0
	            && !isEmpty(trainingStartHour, trainingStartMinute)
	            && !isEmpty(trainingEndHour, trainingEndMinute)) {
	        try {
	            int start = Integer.parseInt(trainingStartHour) * 60 + Integer.parseInt(trainingStartMinute);
	            int end = Integer.parseInt(trainingEndHour) * 60 + Integer.parseInt(trainingEndMinute);
	            if (blankTime > (end - start)) {
	                errors.add(new ObjectError(
	                        "attendanceList[" + index + "].blankTime",
	                        new String[]{"attendance.blankTimeError"},
	                        null,
	                        messageSource.getMessage("attendance.blankTimeError", null, Locale.JAPAN)
	                ));
	            }
	        } catch (NumberFormatException e) {
	            // parseエラーは片方のみ入力チェックで弾かれている
	        }
	    }

	    return errors;
	}

	// ヘルパーメソッド
	private boolean isHalfInput(String h, String m) {
	    return (h != null && !h.isEmpty()) ^ (m != null && !m.isEmpty());
	}

	private boolean isEmpty(String h, String m) {
	    return (h == null || h.isEmpty()) && (m == null || m.isEmpty());
	}
}