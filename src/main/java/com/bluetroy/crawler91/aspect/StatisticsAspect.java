package com.bluetroy.crawler91.aspect;

import com.bluetroy.crawler91.controller.WebSocketController;
import com.bluetroy.crawler91.crawler.Downloader;
import com.bluetroy.crawler91.crawler.dao.MovieStatus;
import com.bluetroy.crawler91.crawler.dao.Repository;
import com.bluetroy.crawler91.crawler.dao.entity.Movie;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;

/**
 * Created with IntelliJ IDEA.
 * Description: 统计包括：扫描视频的数量，filter 结果，download结果 的数据，并通知webSocket通知前端口
 *
 * @author: heyixin
 * Date: 2018-10-13
 * Time: 1:41 AM
 */
//todo 重构 一个类做了两件事情

@Aspect
@Component
public class StatisticsAspect {
    @Autowired
    Repository repository;
    @Autowired
    Downloader downloader;
    @Autowired
    WebSocketController webSocketController;

    private HashMap<String, Movie> downloadingMovies = new HashMap<>();

    @Pointcut("execution(java.util.concurrent.Future com.bluetroy.crawler91.crawler.Downloader.downloadByKey(String)) && args(key)")
    public void downloadPerformance(String key) {
    }

    /**
     * 特例测试：统计扫描视频的数量并通知webSocket通知前端
     */
    @After("execution(void com.bluetroy.crawler91.crawler.Scanner.scanMovies())")
    public void gatherScannedMoviesCount() throws IOException {
        webSocketController.send("/scannedMovies/count", repository.getScannedMovies().size());
    }

    @After("execution(void com.bluetroy.crawler91.crawler.Filter.doFilter())")
    public void gatherFilteredMovies() throws IOException {
        webSocketController.send("/filteredMovies", getData(MovieStatus.FILTERED_MOVIES));
    }

    @After("execution(void com.bluetroy.crawler91.crawler.tools.XpathTool.scanDownloadUrl(*))")
    public void gatherToDownloadMovies() throws IOException {
        sendToDownloadMovies();
    }

    @Around(value = "downloadPerformance(key)", argNames = "proceedingJoinPoint,key")
    public Object gatherDownloadingMovies(ProceedingJoinPoint proceedingJoinPoint, String key) throws Throwable {
        addDownloadingMovie(key);
        Future returnObject = (Future) proceedingJoinPoint.proceed();
        returnObject.get();
        removeDownloadingMovie(key);
        return returnObject;
    }

    @After(value = "downloadPerformance(key))", argNames = "key")
    public void gatherDownloadResult(String key) throws IOException {
        sendDownloadResult();
    }

    public void gatherAllMoviesStatistics() throws IOException {
        gatherScannedMoviesCount();
        gatherFilteredMovies();
        sendToDownloadMovies();
        sendDownloadingMovies();
        sendDownloadResult();
    }

    private void sendDownloadResult() throws IOException {
        webSocketController.send("/downloadedMovies", getData(MovieStatus.DOWNLOADED_MOVIES));
        webSocketController.send("/downloadErrorMovies", getData(MovieStatus.DOWNLOAD_ERROR));
    }

    private void sendToDownloadMovies() throws IOException {
        webSocketController.send("/toDownloadMovies", getData(MovieStatus.TO_DOWNLOAD_MOVIES));
    }

    private void sendDownloadingMovies() throws IOException {
        webSocketController.send("/downloadingMovies", downloadingMovies);
    }

    private void addDownloadingMovie(String key) throws IOException {
        downloadingMovies.put(key, repository.getMovieData(key));
        sendDownloadingMovies();
    }

    private void removeDownloadingMovie(String key) throws IOException {
        downloadingMovies.remove(key);
        sendDownloadingMovies();
    }

    private HashMap getData(MovieStatus movieStatus) {
        return repository.getMoviesData(movieStatus);
    }

}
