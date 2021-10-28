package com.microsoft.appcenter.distribute;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;

@PrepareForTest({
        ProgressDialog.class,
        InstallerUtils.class,
        FileInputStream.class,
        PackageInstaller.SessionCallback.class,
        Distribute.class,
        HandlerUtils.class,
        AppCenterLog.class,
        Toast.class,
        ReleaseInstallerListener.class
})
public class ReleaseInstallerListenerTest {

    @Rule
    public PowerMockRule mPowerMockRule = new PowerMockRule();

    private int mMockSessionId = 1;

    @Mock
    private Context mContext;

    @Mock
    private Activity mActivity;

    @Mock
    private Distribute mDistribute;

    @Mock
    private Toast mToast;

    @Mock
    private ParcelFileDescriptor mMockFileDescriptor;

    @Mock
    private DownloadManager mDownloadManager;

    private ReleaseInstallerListener mReleaseInstallerListener;

    public ReleaseInstallerListenerTest() {
    }

    @Before
    public void setUp() throws Exception {

        /* Mock static classes. */
        mockStatic(InstallerUtils.class);
        mockStatic(HandlerUtils.class);
        mockStatic(Distribute.class);
        mockStatic(AppCenterLog.class);
        mockStatic(Toast.class);

        /* Mock toast. */
        when(Toast.makeText(any(Context.class), anyString(), anyInt())).thenReturn(mToast);

        /* Mock Distribute. */
        when(Distribute.getInstance()).thenReturn(mDistribute);
        doNothing().when(mDistribute).notifyInstallProgress(anyBoolean());

        /* Mock constructors and classes. */
        whenNew(FileInputStream.class).withAnyArguments().thenReturn(mock(FileInputStream.class));
        when(mDownloadManager.openDownloadedFile(anyLong())).thenReturn(mMockFileDescriptor);
        when(mContext.getSystemService(anyString())).thenReturn(mDownloadManager);

        /* Create installer listener. */
        mReleaseInstallerListener = new ReleaseInstallerListener(mContext);
    }

    @After
    public void cleanUp() {
        mReleaseInstallerListener = null;
    }

    @Test
    public void releaseInstallProcessWhenOnFinnishFailure() throws Exception {

        /* Mock progress dialog. */
        android.app.ProgressDialog mockProgressDialog = mock(android.app.ProgressDialog.class);
        whenNew(android.app.ProgressDialog.class).withAnyArguments().thenReturn(mockProgressDialog);
        when(mockProgressDialog.isIndeterminate()).thenReturn(true);

        /* Init install progress dialog. */
        mReleaseInstallerListener.showInstallProgressDialog(mActivity);

        /* Set downloadId. */
        mReleaseInstallerListener.setDownloadId(1);

        /* Start install process. */
        mReleaseInstallerListener.startInstall();

        /* Verify that installPackage method was called. */
        ArgumentCaptor<PackageInstaller.SessionCallback> sessionListener = ArgumentCaptor.forClass(PackageInstaller.SessionCallback.class);
        verifyStatic();
        InstallerUtils.installPackage(Matchers.<InputStream>any(), Matchers.<Context>any(), sessionListener.capture());

        /* Emulate session status. */
        sessionListener.getValue().onCreated(mMockSessionId);

        /* Verify that installer process was triggered in the Distribute. */
        sessionListener.getValue().onActiveChanged(mMockSessionId, true);
        verify(mDistribute).notifyInstallProgress(eq(true));

        /* Verity that progress dialog was updated. */
        sessionListener.getValue().onProgressChanged(mMockSessionId, 1);

        /* Verify that the handler was called and catch runnable. */
        verifyStatic();
        HandlerUtils.runOnUiThread(any(Runnable.class));

        /* Verify that progress dialog was closed after finish install process*/
        sessionListener.getValue().onFinished(mMockSessionId, false);

        /* Verify that the handler was called again. */
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verifyStatic(times(2));
        HandlerUtils.runOnUiThread(runnable.capture());
        runnable.getValue().run();

        /* Verify that installer process was triggered in the Distribute again. */
        verify(mToast).show();
    }

    @Test
    public void throwIOExceptionAfterStartInstall() throws Exception {

        /* Throw exception. */
        PowerMockito.doThrow(new IOException()).when(InstallerUtils.class, "installPackage", any(InputStream.class),  any(Context.class),  any(PackageInstaller.SessionCallback.class));

        /* Set downloadId. */
        mReleaseInstallerListener.setDownloadId(1);

        /* Start install process. */
        mReleaseInstallerListener.startInstall();

        /* Verify that exception was called. */
        verifyStatic();
        AppCenterLog.error(anyString(), anyString(), any(IOException.class));
    }

    @Test
    public void normalReleaseInstallerProcessWhenWithContext() throws Exception {
        normalReleaseInstallerProcess(mReleaseInstallerListener);
    }

    @Test
    public void normalReleaseInstallerProcessWhenContextNull() throws Exception {
        normalReleaseInstallerProcess(new ReleaseInstallerListener(null));
    }

    @Test
    public void normalReleaseInstallerProcessWhenDialogIsIndeterminate() throws Exception {

        /* Mock progress dialog. */
        android.app.ProgressDialog mockProgressDialog = mock(android.app.ProgressDialog.class);
        whenNew(android.app.ProgressDialog.class).withAnyArguments().thenReturn(mockProgressDialog);
        when(mockProgressDialog.isIndeterminate()).thenReturn(false);

        /* Init install progress dialog. */
        mReleaseInstallerListener.showInstallProgressDialog(mActivity);

        /* Set downloadId. */
        mReleaseInstallerListener.setDownloadId(1);

        /* Start install process. */
        mReleaseInstallerListener.startInstall();

        /* Verify that installPackage method was called. */
        ArgumentCaptor<PackageInstaller.SessionCallback> sessionListener = ArgumentCaptor.forClass(PackageInstaller.SessionCallback.class);
        verifyStatic();
        InstallerUtils.installPackage(Matchers.<InputStream>any(), Matchers.<Context>any(), sessionListener.capture());

        /* Emulate session status. */
        sessionListener.getValue().onCreated(mMockSessionId);
        sessionListener.getValue().onBadgingChanged(mMockSessionId);

        /* Verify that installer process was triggered in the Distribute. */
        sessionListener.getValue().onActiveChanged(mMockSessionId, true);
        verify(mDistribute).notifyInstallProgress(eq(true));

        /* Verity that progress dialog was updated. */
        sessionListener.getValue().onProgressChanged(mMockSessionId, 1);

        /* Verify that the handler was called and catch runnable. */
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verifyStatic();
        HandlerUtils.runOnUiThread(runnable.capture());
        runnable.getValue().run();

        /* Verify that the progress dialog was updated. */
        verify(mockProgressDialog).setProgress(anyInt());
        verify(mockProgressDialog).setProgressPercentFormat(any(NumberFormat.class));
        verify(mockProgressDialog).setProgressNumberFormat(anyString());
        verify(mockProgressDialog).setIndeterminate(anyBoolean());

        /* Verify that progress dialog was closed after finish install process*/
        sessionListener.getValue().onFinished(mMockSessionId, true);

        /* Verify that the handler was called again. */
        verifyStatic(times(2));
        HandlerUtils.runOnUiThread(runnable.capture());
        runnable.getValue().run();

        /* Verify that installer process was triggered in the Distribute again. */
        verify(mDistribute).notifyInstallProgress(eq(false));
    }

    public void normalReleaseInstallerProcess(ReleaseInstallerListener listener) throws Exception {

        /* Mock progress dialog. */
        android.app.ProgressDialog mockProgressDialog = mock(android.app.ProgressDialog.class);
        whenNew(android.app.ProgressDialog.class).withAnyArguments().thenReturn(mockProgressDialog);
        when(mockProgressDialog.isIndeterminate()).thenReturn(true);

        /* Init install progress dialog. */
        mReleaseInstallerListener.showInstallProgressDialog(mActivity);

        /* Set downloadId. */
        mReleaseInstallerListener.setDownloadId(1);

        /* Start install process. */
        mReleaseInstallerListener.startInstall();

        /* Verify that installPackage method was called. */
        ArgumentCaptor<PackageInstaller.SessionCallback> sessionListener = ArgumentCaptor.forClass(PackageInstaller.SessionCallback.class);
        verifyStatic();
        InstallerUtils.installPackage(Matchers.<InputStream>any(), Matchers.<Context>any(), sessionListener.capture());

        /* Emulate session status. */
        sessionListener.getValue().onCreated(mMockSessionId);
        sessionListener.getValue().onBadgingChanged(mMockSessionId);

        /* Verify that installer process was triggered in the Distribute. */
        sessionListener.getValue().onActiveChanged(mMockSessionId, true);
        verify(mDistribute).notifyInstallProgress(eq(true));

        /* Verity that progress dialog was updated. */
        sessionListener.getValue().onProgressChanged(mMockSessionId, 1);

        /* Verify that the handler was called and catch runnable. */
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verifyStatic();
        HandlerUtils.runOnUiThread(runnable.capture());
        runnable.getValue().run();

        /* Verify that the progress dialog was updated. */
        verify(mockProgressDialog).setProgress(anyInt());

        /* Verify that progress dialog was closed after finish install process*/
        sessionListener.getValue().onFinished(mMockSessionId, true);

        /* Verify that the handler was called again. */
        verifyStatic(times(2));
        HandlerUtils.runOnUiThread(runnable.capture());
        runnable.getValue().run();

        /* Verify that installer process was triggered in the Distribute again. */
        verify(mDistribute).notifyInstallProgress(eq(false));
    }
}
