package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.AbstractHttpConnection;

import java.io.IOException;

/**
 * 代表一个http连接连接
 */
public class AsyncHttpConnection extends AbstractHttpConnection {
    private SelectChannelEndPoint _asyncEndp;

    public AsyncHttpConnection(SelectChannelEndPoint endPoint){
        this._asyncEndp = endPoint;
    }

    /**
     * http://www.west999.com/cms/wiki/code/2018-07-20/41829.html-长连接坑
     * 总之就是处理请求了
     * @return
     */
    @Override
    public Connection handle() throws IOException
    {
        Connection connection = this;
        boolean some_progress=false;
        boolean progress=true;

        try
        {
            setCurrentConnection(this);

            // don't check for idle while dispatched (unless blocking IO is done).
            _asyncEndp.setCheckForIdle(false);


            // While progress and the connection has not changed
            while (progress && connection==this)
            {
                progress=false;
                try
                {
                    // Handle resumed request
                    if (_request._async.isAsync())
                    {
                        if (_request._async.isDispatchable())
                            handleRequest();
                    }
                    // else Parse more input
                    else if (!_parser.isComplete() && _parser.parseAvailable())
                        progress=true;

                    // Generate more output
                    if (_generator.isCommitted() && !_generator.isComplete() && !_endp.isOutputShutdown() && !_request.getAsyncContinuation().isAsyncStarted())
                        if (_generator.flushBuffer()>0)
                            progress=true;

                    // Flush output
                    _endp.flush();

                    // Has any IO been done by the endpoint itself since last loop
                    if (_asyncEndp.hasProgressed())
                        progress=true;
                }
                catch (HttpException e)
                {
                    if (LOG.isDebugEnabled())
                    {
                        LOG.debug("uri="+_uri);
                        LOG.debug("fields="+_requestFields);
                        LOG.debug(e);
                    }
                    progress=true;
                    _generator.sendError(e.getStatus(), e.getReason(), null, true);
                }
                finally
                {
                    some_progress|=progress;
                    //  Is this request/response round complete and are fully flushed?
                    boolean parserComplete = _parser.isComplete();
                    boolean generatorComplete = _generator.isComplete();
                    boolean complete = parserComplete && generatorComplete;
                    if (parserComplete)
                    {
                        if (generatorComplete)
                        {
                            // Reset the parser/generator
                            progress=true;

                            // look for a switched connection instance?
                            if (_response.getStatus()==HttpStatus.SWITCHING_PROTOCOLS_101)
                            {
                                Connection switched=(Connection)_request.getAttribute("org.eclipse.jetty.io.Connection");
                                if (switched!=null)
                                    connection=switched;
                            }

                            reset();

                            // TODO Is this still required?
                            if (!_generator.isPersistent() && !_endp.isOutputShutdown())
                            {
                                LOG.warn("Safety net oshut!!!  IF YOU SEE THIS, PLEASE RAISE BUGZILLA");
                                _endp.shutdownOutput();
                            }
                        }
                        else
                        {
                            // We have finished parsing, but not generating so
                            // we must not be interested in reading until we
                            // have finished generating and we reset the generator
                            _readInterested = false;
                            LOG.debug("Disabled read interest while writing response {}", _endp);
                        }
                    }

                    if (!complete && _request.getAsyncContinuation().isAsyncStarted())
                    {
                        // The request is suspended, so even though progress has been made,
                        // exit the while loop by setting progress to false
                        LOG.debug("suspended {}",this);
                        progress=false;
                    }
                }
            }
        }
        finally
        {
            setCurrentConnection(null);

            // If we are not suspended
            if (!_request.getAsyncContinuation().isAsyncStarted())
            {
                // return buffers
                _parser.returnBuffers();
                _generator.returnBuffers();

                // reenable idle checking unless request is suspended
                _asyncEndp.setCheckForIdle(true);
            }

            // Safety net to catch spinning
            if (some_progress)
                _total_no_progress=0;
            else
            {
                _total_no_progress++;
                if (NO_PROGRESS_INFO>0 && _total_no_progress%NO_PROGRESS_INFO==0 && (NO_PROGRESS_CLOSE<=0 || _total_no_progress< NO_PROGRESS_CLOSE))
                    LOG.info("EndPoint making no progress: "+_total_no_progress+" "+_endp+" "+this);
                if (NO_PROGRESS_CLOSE>0 && _total_no_progress==NO_PROGRESS_CLOSE)
                {
                    LOG.warn("Closing EndPoint making no progress: "+_total_no_progress+" "+_endp+" "+this);
                    if (_endp instanceof SelectChannelEndPoint)
                        ((SelectChannelEndPoint)_endp).getChannel().close();
                }
            }
        }
        return connection;
    }

    public void onInputShutdown() throws IOException
    {
        // If we don't have a committed response and we are not suspended
        if (_generator.isIdle() && !_request.getAsyncContinuation().isSuspended())
        {
            // then no more can happen, so close.
            _endp.close();
        }

        // Make idle parser seek EOF
        if (_parser.isIdle())
            _parser.setPersistent(false);
    }

    @Override
    public void reset()
    {
        _readInterested = true;
        LOG.debug("Enabled read interest {}", _endp);
        super.reset();
    }

    @Override
    public boolean isSuspended()
    {
        return !_readInterested || super.isSuspended();
    }

}
