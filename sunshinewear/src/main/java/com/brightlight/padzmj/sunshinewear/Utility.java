package com.brightlight.padzmj.sunshinewear;


import android.content.res.Resources;
import android.support.annotation.NonNull;

import java.util.Calendar;

/**
 * Created by PadzMJ on 06/09/2016.
 */

public class Utility {


    @NonNull
    public static String getMonthOfYearString(Resources resources, int monthOfYear) {
        int monthOfYearString;
        switch(monthOfYear) {
            case Calendar.JANUARY:
                monthOfYearString = R.string.january;
                break;
            case Calendar.FEBRUARY:
                monthOfYearString = R.string.february;
                break;
            case Calendar.MARCH:
                monthOfYearString = R.string.march;
                break;
            case Calendar.APRIL:
                monthOfYearString = R.string.april;
                break;
            case Calendar.MAY:
                monthOfYearString = R.string.may;
                break;
            case Calendar.JUNE:
                monthOfYearString = R.string.june;
                break;
            case Calendar.JULY:
                monthOfYearString = R.string.july;
                break;
            case Calendar.AUGUST:
                monthOfYearString = R.string.august;
                break;
            case Calendar.SEPTEMBER:
                monthOfYearString = R.string.september;
                break;
            case Calendar.OCTOBER:
                monthOfYearString = R.string.october;
                break;
            case Calendar.NOVEMBER:
                monthOfYearString = R.string.november;
                break;
            case Calendar.DECEMBER:
                monthOfYearString = R.string.december;
                break;
            default:
                monthOfYearString = -1;
        }

        if (monthOfYearString != -1) {
            return resources.getString(monthOfYearString);
        }

        return "";
    }

    @NonNull
    public static String getDayOfWeekString(Resources resources, int day) {
        int dayOfWeekString;
        switch (day) {
            case Calendar.SUNDAY:
                dayOfWeekString = R.string.sunday;
                break;
            case Calendar.MONDAY:
                dayOfWeekString = R.string.monday;
                break;
            case Calendar.TUESDAY:
                dayOfWeekString = R.string.tuesday;
                break;
            case Calendar.WEDNESDAY:
                dayOfWeekString = R.string.wednesday;
                break;
            case Calendar.THURSDAY:
                dayOfWeekString = R.string.thursday;
                break;
            case Calendar.FRIDAY:
                dayOfWeekString = R.string.friday;
                break;
            case Calendar.SATURDAY:
                dayOfWeekString = R.string.saturday;
                break;
            default:
                dayOfWeekString = -1;
        }

        if (dayOfWeekString != -1) {
            return resources.getString(dayOfWeekString);
        }

        return "";
    }
}
