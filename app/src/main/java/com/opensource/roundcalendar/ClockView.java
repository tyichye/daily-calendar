package com.opensource.roundcalendar;

import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.AppCompatImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.text.Format;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class ClockView extends AppCompatImageView
{
    private Map<String, Paint> paints;
    private ClockWidget clockWidget;
    private static final int backgroundColor = Color.TRANSPARENT;
    private static final int refreshTimeoutMillis = 1800000; // 30 minutes - minimal valid value
    private CalendarAdapter calendarAdapter = null;
    private boolean useCalendarColors = false;


    public ClockView(Context context)
    {
        super(context);
    }

    public ClockView(Context context, Point screenSize, boolean useCalendarColors)
    {
        super(context);
        this.useCalendarColors = useCalendarColors;
        clockWidget = new ClockWidget(screenSize);
        paints = initPaints();
    }


    private Map<String, Paint> initPaints()
    {
        Map <String, Paint> paints = new HashMap<>();
        Paint ringPaint = new Paint();
        ringPaint.setColor(Color.BLACK);
        ringPaint.setStrokeWidth(clockWidget.getPaddingRadius()*2);
        ringPaint.setAntiAlias(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setAlpha(70);
        paints.put("ring", ringPaint);

        Paint borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStrokeWidth(6);
        paints.put("border", borderPaint);

        Paint clockHandPaint = new Paint();
        clockHandPaint.setColor(Color.RED);
        clockHandPaint.setStrokeWidth(clockWidget.getHandWidth());
        paints.put("hand", clockHandPaint);

        Paint bigDigitsPaint = new Paint();
        bigDigitsPaint.setTextSize(clockWidget.getBigDigitSize());
        bigDigitsPaint.setTextAlign(Paint.Align.CENTER);
        bigDigitsPaint.setColor(clockWidget.getDigitColor());
        paints.put("bigDigits", bigDigitsPaint);

        Paint datePaint = new Paint();
        datePaint.setTextSize(clockWidget.getDateSize());
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setColor(clockWidget.getDigitColor());
        paints.put("date", datePaint);


        Paint textTitlePaint = new Paint();
        textTitlePaint.setTextSize(clockWidget.getTitleSize());
        textTitlePaint.setColor(clockWidget.getEventTitleColor());
        paints.put("title", textTitlePaint);

        for (Paint p : paints.values()) {
            p.setAntiAlias(true);
        }
        return paints;

    }

    void drawAll(Canvas canvas, RemoteViews remoteViews){
        canvas.drawColor(backgroundColor);
        drawClock(canvas);
        if (calendarAdapter != null) {
            drawEvents(canvas, remoteViews);
            if (!calendarAdapter.isCalendarShifted()) {
                drawHand(canvas);
                drawTimeToNextEvent(remoteViews);
            }
            else{
                remoteViews.setTextViewText(R.id.time_to_next_event_textView, "");
            }
        }

        drawMarkersAndDigits(canvas);
        postInvalidateDelayed(refreshTimeoutMillis);
    }

    void setCalendarAdapter()
    {
        calendarAdapter = CalendarAdapter.getInstance();
        invalidate();
    }

    private void drawClock(Canvas canvas)
    {
        RectF ringRect = clockWidget.getWidgetCircleObject();

        // draw base ring
        canvas.drawArc(ringRect, -90, 360, false, paints.get("ring"));

//         draw clock border
        //subtracted 75 from radius than added 100 to stroke , overall +25 to border
        canvas.drawCircle(clockWidget.getCenter().x,
                clockWidget.getCenter().y,
                clockWidget.getRadius() + clockWidget.getPaddingDigits(),
                paints.get("border"));
    }

    private void drawMarkersAndDigits(Canvas canvas)
    {
        // draw markers
        List<List<Point>> markers = clockWidget.getHourMarkersCoordinates();
        for (List<Point> marker : markers) {
            canvas.drawLine(marker.get(0).x, marker.get(0).y,
                    marker.get(1).x, marker.get(1).y,
                    paints.get("border"));
        }

        // draw digits
        List<Point> digits = clockWidget.getDigitsCoordinates();
        for (int i = 0; i < digits.size(); i++) {
            Paint paint;
            if (i % 3 == 0)
            {
                paint = paints.get("bigDigits");
                Point coords = digits.get(i);
                canvas.drawText(Integer.toString(i), coords.x, coords.y, paint);
            }
        }
    }

    private void drawHand(Canvas canvas) {
        List<Point> hand = clockWidget.getCurrentTimeHandCoordinates();
        canvas.drawLine(hand.get(0).x, hand.get(0).y, hand.get(1).x, hand.get(1).y, paints.get("hand"));
    }

    private void drawTimeToNextEvent(RemoteViews views) {
        long timeToNextEvent = calendarAdapter.getTimeToNextEvent();
        if (timeToNextEvent != 0){
            long timeInMinutes = (timeToNextEvent - System.currentTimeMillis())/60_000;
            String text = "Time to next event: " + String.valueOf(timeInMinutes) + " minutes";
            views.setTextViewText(R.id.time_to_next_event_textView, text);
        }
        else {
            views.setTextViewText(R.id.time_to_next_event_textView, "");
        }
    }


    private void drawEvents(Canvas canvas, RemoteViews remoteViews) {
        RectF widgetCircle = clockWidget.getWidgetCircleObject();

        List<Event> todayEvents = calendarAdapter.getTodayEvents();

        // draw overlapping events
        List<List<Event>> sameTimeEventsList = extractSameTimeEvents(todayEvents);
        for (List<Event> sameTimeEvents : sameTimeEventsList) {
            drawSameTimeEvents(canvas, widgetCircle, sameTimeEvents);
        }

        List<Event> allDayEvents = new ArrayList<>();
        for (Event event : todayEvents) {
            if (event.isAllDay()) {
                allDayEvents.add(event);
                continue;
            }
            drawEvent(canvas, widgetCircle, event);
        }

        if (allDayEvents.isEmpty()) {
            remoteViews.setTextViewText(R.id.all_day_event_textView, "");
            return;
        }

        HashSet<String> allDayTitles = new HashSet<>();
        for(Event event : allDayEvents){
            allDayTitles.add(event.getTitle());
        }

        // build the string that represent the all day events and show it as text under the clock
        StringBuilder builder = new StringBuilder();

        builder.append("All-day: ");
        for (String title : allDayTitles) {
            builder.append(title.trim());
            builder.append(", ");
        }
        builder.setLength(builder.length() - 2); // cut out last comma
        remoteViews.setTextViewText(R.id.all_day_event_textView, cutAllDayEventsTitlesIfNeeded(builder.toString()));
    }

    private String cutEventTitleIfNeeded(String title) {
        return normalizeEventTitle(title, clockWidget.getPaddingRadius()*2);
    }

    private String cutAllDayEventsTitlesIfNeeded(String titles) {
        return normalizeEventTitle(titles, clockWidget.getWidgetWidth());
    }

    private String normalizeEventTitle(String title, float maxWidth) {
        String newTitle = title;
        float textWidth = paints.get("title").measureText(title);
        if (textWidth > maxWidth) {
            float maxSymbols = (maxWidth * title.length()) / textWidth;
            maxSymbols -= 3; // "..." + one padding char
            newTitle = title.substring(0, Math.round(maxSymbols));
            newTitle += "..";
        }
        return newTitle;
    }


    private void drawEvent(Canvas canvas, RectF widgetCircle, Event event) {
        Calendar calendar = Calendar.getInstance();

        if (event.isEnd(calendar.getTimeInMillis()) && !calendarAdapter.isCalendarShifted())
        {
            drawEventGeneralized(canvas, widgetCircle, clockWidget.getEventDegrees(event), Color.GRAY,
                    event.isFinishedInFirstDayHalf(), event.getTitle());
            return;
        }

        drawEventGeneralized(canvas, widgetCircle, clockWidget.getEventDegrees(event), event.getColor(),
                event.isFinishedInFirstDayHalf(), event.getTitle());
    }

    private void drawSameTimeEvents(Canvas canvas, RectF widgetCircle, List<Event> events) {
        StringBuilder titleBuilder = new StringBuilder();
        titleBuilder.append(events.size());
        titleBuilder.append(": ");
        for (Event event : events) {
            titleBuilder.append(event.getTitle());
            titleBuilder.append(", ");
        }
        titleBuilder.setLength(titleBuilder.length() - 2); // cut out last comma

        Event event = events.get(0);
        drawEventGeneralized(canvas, widgetCircle, clockWidget.getEventDegrees(event), event.getColor(),
                event.isFinishedInFirstDayHalf(), titleBuilder.toString());
    }


    private void drawEventGeneralized(Canvas canvas, RectF widgetCircle, ClockWidget.EventDegreeData degrees, int color,
                                      boolean isFinishedFirstDayHalf, String title) {
        Paint eventPaint = paints.get("ring");
        if (useCalendarColors) {
            eventPaint.setColor(color);
        }
        else
        {
            eventPaint.setColor(Color.parseColor("#FF81C4FD"));
        }
        eventPaint.setAlpha(80);
        final float minSweep = (float) 0.5;
        float sweepAngle = Math.max(degrees.getSweep(), minSweep);
        float startAngle = degrees.getStart();
        canvas.drawArc(widgetCircle, startAngle, sweepAngle, false, eventPaint);

        eventPaint.setColor(ColorUtils.blendARGB(eventPaint.getColor(), Color.BLACK, 0.1f));
        canvas.drawArc(clockWidget.getWidgetCircleObject(), startAngle, sweepAngle, false, eventPaint);
        canvas.save();

        final String titleNormalized = cutEventTitleIfNeeded(title);
        /*
            α = arcsin(l / (2 * R)) * 360 / π
            where l - horde length (text height)
        */
        double titleTextAngle = Math.toDegrees(Math.asin(Math.toRadians(calculateTextHeight(titleNormalized) /
                (2 * clockWidget.getRadius())))) * (double) 360 / Math.PI;
        // half of text angle is needed to center it
        titleTextAngle /= 2;
        float titleAngle = degrees.getStart() + degrees.getSweep() / 2 + 90;
        final float rotateAngle;
        final int padding;
        if (isFinishedFirstDayHalf)
        {
            // move forward on half of text angle
            titleAngle += (float) titleTextAngle; 
            rotateAngle = titleAngle - 90;
            // title text: center->radius // need to change according to stroke width
            padding = calculateTextWidth(titleNormalized); 
        } else {
            // move backward on half of text angle
            titleAngle -= (float) titleTextAngle; 
            rotateAngle = titleAngle - 270;
            // title text: radius->center
            padding = 0; 
        }

        Point eventTitlePoint = clockWidget.calculateEventTitlePoint(titleAngle, padding);
        canvas.rotate(rotateAngle, eventTitlePoint.x, eventTitlePoint.y);
        canvas.drawText(titleNormalized, eventTitlePoint.x, eventTitlePoint.y, paints.get("title"));
        canvas.restore();
    }

    private int calculateTextWidth(String text) {
        Rect bounds = new Rect();
        paints.get("title").getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    private int calculateTextHeight(String text) {
        Rect bounds = new Rect();
        paints.get("title").getTextBounds(text, 0, text.length(), bounds);
        return bounds.height();
    }

    private List<List<Event>> extractSameTimeEvents(List<Event> events) {
        List<List<Event>> result = findSameTimeEvents(events);
        for (List<Event> sameTimeEvents : result) {
            removeEventsFromList(events, sameTimeEvents);
        }
        return result;
    }

    private List<List<Event>> findSameTimeEvents(List<Event> events) {
        List<List<Event>> result = new ArrayList<>();
        List<Integer> foundIndexes = new ArrayList<>();
        for (int outerIndex = 0; outerIndex < events.size(); outerIndex++) {
            Event event = events.get(outerIndex);
            if (event.isAllDay()) {
                continue;
            }
            String startTime = event.getStartTime();
            String finishTime = event.getFinishTime();
            List<Event> sameTimeEvents = new ArrayList<>();
            sameTimeEvents.add(event);
            for (int innerIndex = 0; innerIndex < events.size(); innerIndex++) {
                if ((innerIndex == outerIndex) || foundIndexes.contains(innerIndex)) {
                    continue;
                }
                Event anotherEvent = events.get(innerIndex);
                if (anotherEvent.getStartTime().equals(startTime) && anotherEvent.getFinishTime().equals(finishTime)) {
                    sameTimeEvents.add(anotherEvent);
                    foundIndexes.add(innerIndex);
                }
            }
            if (sameTimeEvents.size() > 1) {
                result.add(sameTimeEvents);
                foundIndexes.add(outerIndex);
            }
        }
        return result;
    }

    private void removeEventsFromList(List<Event> eventsList, List<Event> eventsToRemove) {
        for (Iterator<Event> iter = eventsList.listIterator(); iter.hasNext(); ) {
            if (eventsToRemove.contains(iter.next())) {
                iter.remove();
            }
        }
    }




}
